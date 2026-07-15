package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.UserGoal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserGoalsRepositoryImpl @Inject constructor(
    private val userGoalDao: UserGoalDao,
    private val supabase: SupabaseClient,
) : UserGoalsRepository {

    override fun observeGoal(userId: String): Flow<UserGoal?> {
        return userGoalDao.observeForUser(userId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun saveGoal(goal: UserGoal): UserGoal {
        userGoalDao.upsert(goal.toEntity(isDirty = true, syncedAt = null))
        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(goal) {
                    select()
                }
                .decodeSingle<UserGoal>()
            userGoalDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user goal to Supabase", e)
            return goal
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = userGoalDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<UserGoal>()
                    userGoalDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty user goal id=${entity.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndSync failed", e)
        }
    }

    override suspend fun refresh(userId: String) {
        fetchAndSync()
        try {
            val remoteGoal = supabase.from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    limit(1)
                }
                .decodeList<UserGoal>()
                .firstOrNull()

            val hasDirty = userGoalDao.getDirty(userId).isNotEmpty()
            when {
                hasDirty -> {
                    // Keep local dirty goal; still cache remote under a different id is avoided.
                }
                remoteGoal != null -> {
                    userGoalDao.clearAndInsert(userId, remoteGoal.toEntity(isDirty = false))
                }
                else -> {
                    userGoalDao.deleteSyncedForUser(userId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh user goal from Supabase", e)
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private companion object {
        const val TAG = "UserGoalsRepository"
        const val TABLE_NAME = "user_goals"
    }
}
