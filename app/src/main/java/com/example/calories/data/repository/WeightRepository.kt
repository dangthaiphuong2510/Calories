package com.example.calories.data.repository

import com.example.calories.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WeightRepository {
    fun observeWeightEntries(userId: String): Flow<List<WeightEntry>>
    suspend fun addWeightEntry(weightKg: Double, recordedAt: String): WeightEntry
    /** Updates the single weight log for [date] in place so steppers don't flood history. */
    suspend fun upsertWeightForDate(weightKg: Double, date: LocalDate): WeightEntry
    suspend fun deleteWeightEntry(id: String)
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
