package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.ProfileDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val supabase: SupabaseClient,
) : ProfileRepository {

    override fun observeProfile(userId: String): Flow<Profile?> {
        return profileDao.observeById(userId).map { it?.toDomain() }
    }

    override suspend fun upsertProfile(
        displayName: String?,
        avatarUrl: String?,
        gender: String?,
        birthDate: String?,
    ): Profile {
        val userId = requireCurrentUserId()
        val existing = profileDao.getById(userId)?.toDomain()
        val profile = Profile(
            id = userId,
            displayName = displayName ?: existing?.displayName,
            avatarUrl = avatarUrl ?: existing?.avatarUrl,
            gender = gender ?: existing?.gender,
            birthDate = birthDate ?: existing?.birthDate,
        )
        profileDao.upsert(profile.toEntity(isDirty = true, syncedAt = null))

        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(profile) {
                    select()
                }
                .decodeSingle<Profile>()
            profileDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync profile to Supabase", e)
            return profile
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val dirty = profileDao.getDirty()
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<Profile>()
                    profileDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty profile id=${entity.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndSync failed", e)
        }
    }

    override suspend fun refresh(userId: String) {
        fetchAndSync()
        try {
            val remote = supabase.from(TABLE_NAME)
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<Profile>()
                .firstOrNull()

            if (remote != null) {
                val localDirty = profileDao.getById(userId)?.isDirty == true
                if (!localDirty) {
                    profileDao.upsert(remote.toEntity(isDirty = false))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh profile from Supabase", e)
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val TABLE_NAME = "profiles"
    }
}
