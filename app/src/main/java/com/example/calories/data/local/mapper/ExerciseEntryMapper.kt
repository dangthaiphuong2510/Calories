package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.ExerciseEntryEntity
import com.example.calories.model.ExerciseEntry

fun ExerciseEntryEntity.toDomain(): ExerciseEntry = ExerciseEntry(
    id = id,
    userId = userId,
    name = name,
    caloriesBurned = caloriesBurned,
    durationMinutes = durationMinutes,
    createdAt = createdAt,
)

fun ExerciseEntry.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): ExerciseEntryEntity = ExerciseEntryEntity(
    id = id,
    userId = userId,
    name = name,
    caloriesBurned = caloriesBurned,
    durationMinutes = durationMinutes,
    createdAt = createdAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
