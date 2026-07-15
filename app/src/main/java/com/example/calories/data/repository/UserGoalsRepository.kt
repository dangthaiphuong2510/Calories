package com.example.calories.data.repository

import com.example.calories.model.UserGoal
import kotlinx.coroutines.flow.Flow

interface UserGoalsRepository {
    fun observeGoal(userId: String): Flow<UserGoal?>
    suspend fun saveGoal(goal: UserGoal): UserGoal
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
