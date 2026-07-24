package com.example.calories.data.repository

import com.example.calories.model.WaterEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface WaterRepository {
    fun observeWaterEntries(userId: String): Flow<List<WaterEntry>>
    fun observeTotalMlForDate(userId: String, date: LocalDate): Flow<Int>
    suspend fun getTotalMlForDate(userId: String, date: LocalDate): Int
    suspend fun addWaterEntry(
        amountMl: Int,
        createdAt: String = com.example.calories.util.DateTimeUtils.nowIso(),
        userIdOverride: String? = null,
    ): WaterEntry
    /** Deletes the newest entry for [date] matching [amountMl] (e.g. undo a 250ml step). */
    suspend fun removeLastForDate(date: LocalDate, amountMl: Int): Boolean
    suspend fun deleteWaterEntry(id: String)
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
