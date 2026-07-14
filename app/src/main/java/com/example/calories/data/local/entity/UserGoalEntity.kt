package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_goals")
data class UserGoalEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val targetWeight: Double,
    val currentWeight: Double,
    val age: Int,
    val gender: String,
    val heightCm: Double,
    val activityLevel: String,
    val goalType: String,
    val tdee: Int,
    val dailyCalories: Int,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
