package com.example.calories.data.repository

import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.data.remote.supabase.SupabaseGoalsService
import com.example.calories.model.UserGoal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserGoalsRepositoryImpl @Inject constructor(
    private val userGoalDao: UserGoalDao,
    private val remote: SupabaseGoalsService,
) : UserGoalsRepository {

    override fun observeGoal(userId: String): Flow<UserGoal?> {
        return userGoalDao.observeForUser(userId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun saveGoal(goal: UserGoal): UserGoal {
        val remoteGoal = remote.upsertGoal(goal)
        userGoalDao.upsert(remoteGoal.toEntity())
        return remoteGoal
    }

    override suspend fun refresh(userId: String) {
        val remoteGoal = remote.getGoalForCurrentUser()
        if (remoteGoal != null) {
            userGoalDao.clearAndInsert(userId, remoteGoal.toEntity())
        } else {
            userGoalDao.deleteForUser(userId)
        }
    }
}
