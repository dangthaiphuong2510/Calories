package com.example.calories.data.repository

import com.example.calories.model.ExerciseEntry
import com.example.calories.model.ExercisePreset
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface ExerciseRepository {
    fun observeExerciseEntries(userId: String): Flow<List<ExerciseEntry>>
    fun observeExercisesForDate(userId: String, date: LocalDate): Flow<List<ExerciseEntry>>
    fun observeCustomExercises(): Flow<List<ExercisePreset>>
    suspend fun addExerciseEntry(
        name: String,
        caloriesBurned: Double,
        durationMinutes: Int,
        createdAt: String = com.example.calories.util.DateTimeUtils.nowIso(),
    ): ExerciseEntry
    suspend fun deleteExerciseEntry(id: String)
    suspend fun addCustomExercise(
        name: String,
        calories: Int,
        durationMinutes: Int,
    ): ExercisePreset
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
