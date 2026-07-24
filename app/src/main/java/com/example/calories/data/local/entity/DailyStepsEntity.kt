package com.example.calories.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "daily_steps",
    primaryKeys = ["date", "userId"],
)
data class DailyStepsEntity(
    val date: String,
    val userId: String,
    val stepCount: Long,
    val caloriesBurned: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDirty: Boolean = true,
)
