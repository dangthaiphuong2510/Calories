package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.WaterEntryEntity
import com.example.calories.model.WaterEntry

fun WaterEntryEntity.toDomain(): WaterEntry = WaterEntry(
    id = id,
    userId = userId,
    amountMl = amountMl,
    createdAt = createdAt,
)

fun WaterEntry.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): WaterEntryEntity = WaterEntryEntity(
    id = id,
    userId = userId,
    amountMl = amountMl,
    createdAt = createdAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
