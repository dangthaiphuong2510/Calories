package com.example.calories.data.repository

import com.example.calories.model.WeightEntry
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first weight tracking repository.
 *
 * Sync pattern:
 * 1. [observeWeightEntries] reads from Room (`Flow`).
 * 2. [addWeightEntry] writes Supabase first, then upserts Room.
 * 3. Realtime remote events upsert/delete Room rows.
 * 4. [refresh] fetches remote list and clearAndInserts into Room.
 */
interface WeightRepository {
    fun observeWeightEntries(userId: String): Flow<List<WeightEntry>>
    suspend fun addWeightEntry(weightKg: Double, recordedAt: String): WeightEntry
    suspend fun deleteWeightEntry(id: String)
    suspend fun refresh(userId: String)
}
