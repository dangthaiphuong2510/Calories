package com.example.calories.data.repository

import com.example.calories.model.UserGoal
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first user goals repository.
 *
 * Sync pattern:
 * 1. [observeGoal] reads from Room (`Flow`).
 * 2. [saveGoal] writes Supabase first, then upserts Room.
 * 3. Realtime remote events upsert Room.
 * 4. [refresh] fetches remote goal and replaces the local row.
 */
interface UserGoalsRepository {
    fun observeGoal(userId: String): Flow<UserGoal?>
    suspend fun saveGoal(goal: UserGoal): UserGoal
    suspend fun refresh(userId: String)
}
