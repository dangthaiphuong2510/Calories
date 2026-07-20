package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.FoodEntryEntity
import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType

fun FoodEntryEntity.toDomain(): FoodEntry = FoodEntry(
    id = id,
    userId = userId,
    name = name,
    calories = calories,
    protein = protein,
    carb = carb,
    fat = fat,
    mealType = MealType.entries.find { it.name.equals(mealType, ignoreCase = true) }
        ?: MealType.SNACKS,
    servingGrams = servingGrams,
    createdAt = createdAt,
)

fun FoodEntry.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): FoodEntryEntity = FoodEntryEntity(
    id = id,
    userId = userId,
    name = name,
    calories = calories,
    protein = protein,
    carb = carb,
    fat = fat,
    mealType = mealType.name,
    servingGrams = servingGrams,
    createdAt = createdAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
