package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weight_entries")
data class WeightEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val weightKg: Double,
    val recordedAt: String,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
