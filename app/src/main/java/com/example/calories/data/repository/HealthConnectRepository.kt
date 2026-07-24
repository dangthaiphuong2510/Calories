package com.example.calories.data.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface HealthConnectRepository {
    fun observeStepsForDate(userId: String, date: LocalDate): Flow<Long?>
    suspend fun syncFromHealthConnect()
    suspend fun syncDailySteps()
}
