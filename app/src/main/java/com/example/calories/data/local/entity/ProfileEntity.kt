package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val gender: String? = null,
    val birthDate: String? = null,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
