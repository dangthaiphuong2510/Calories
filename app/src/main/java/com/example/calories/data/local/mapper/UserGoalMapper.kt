package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.UserGoalEntity
import com.example.calories.model.UserGoal
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType

fun UserGoalEntity.toDomain(): UserGoal = UserGoal(
    id = id,
    userId = userId,
    targetWeight = targetWeight,
    currentWeight = currentWeight,
    age = age,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.MALE),
    heightCm = heightCm,
    activityLevel = runCatching { ActivityLevel.valueOf(activityLevel) }
        .getOrDefault(ActivityLevel.SEDENTARY),
    goalType = runCatching { GoalType.valueOf(goalType) }.getOrDefault(GoalType.MAINTAIN),
    tdee = tdee,
    dailyCalories = dailyCalories,
)

fun UserGoal.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): UserGoalEntity = UserGoalEntity(
    id = id,
    userId = userId,
    targetWeight = targetWeight,
    currentWeight = currentWeight,
    age = age,
    gender = gender.name,
    heightCm = heightCm,
    activityLevel = activityLevel.name,
    goalType = goalType.name,
    tdee = tdee,
    dailyCalories = dailyCalories,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
