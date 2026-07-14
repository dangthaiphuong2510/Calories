package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.FridgeIngredientEntity
import com.example.calories.model.FridgeIngredient

fun FridgeIngredientEntity.toDomain(): FridgeIngredient = FridgeIngredient(
    id = id,
    userId = userId,
    name = name,
    quantity = quantity,
    unit = unit,
    expiryDate = expiryDate,
)

fun FridgeIngredient.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): FridgeIngredientEntity = FridgeIngredientEntity(
    id = id,
    userId = userId,
    name = name,
    quantity = quantity,
    unit = unit,
    expiryDate = expiryDate,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
