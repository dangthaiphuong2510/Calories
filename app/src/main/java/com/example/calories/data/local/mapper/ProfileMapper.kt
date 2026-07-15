package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.ProfileEntity
import com.example.calories.model.Profile

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    gender = gender,
    birthDate = birthDate,
)

fun Profile.toEntity(
    isDirty: Boolean = false,
    syncedAt: Long? = System.currentTimeMillis(),
): ProfileEntity = ProfileEntity(
    id = id,
    displayName = displayName,
    avatarUrl = avatarUrl,
    gender = gender,
    birthDate = birthDate,
    isDirty = isDirty,
    syncedAt = syncedAt,
)
