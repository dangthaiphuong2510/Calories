package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.WeightEntryEntity
import com.example.calories.model.WeightEntry

fun WeightEntryEntity.toDomain(): WeightEntry = WeightEntry(
    id = id,
    userId = userId,
    weightKg = weightKg,
    recordedAt = recordedAt,
)

fun WeightEntry.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): WeightEntryEntity = WeightEntryEntity(
    id = id,
    userId = userId,
    weightKg = weightKg,
    recordedAt = recordedAt,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
