package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.DailyStepsEntity
import com.example.calories.model.DailySteps
import java.time.Instant

fun DailyStepsEntity.toDomain(): DailySteps = DailySteps(
    date = date,
    userId = userId,
    stepCount = stepCount,
    caloriesBurned = caloriesBurned,
    createdAt = Instant.ofEpochMilli(createdAt).toString(),
    updatedAt = Instant.ofEpochMilli(updatedAt).toString(),
    isDirty = false,
)

fun DailySteps.toEntity(isDirty: Boolean = false): DailyStepsEntity = DailyStepsEntity(
    date = date,
    userId = userId,
    stepCount = stepCount,
    caloriesBurned = caloriesBurned,
    createdAt = Instant.parse(createdAt).toEpochMilli(),
    updatedAt = Instant.parse(updatedAt).toEpochMilli(),
    isDirty = isDirty,
)
