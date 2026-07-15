package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_entries")
data class WaterEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val amountMl: Int,
    val createdAt: String,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
