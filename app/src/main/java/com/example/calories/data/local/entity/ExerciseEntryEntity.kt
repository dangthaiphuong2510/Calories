package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercise_entries")
data class ExerciseEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val caloriesBurned: Double,
    val durationMinutes: Int,
    val createdAt: String,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
