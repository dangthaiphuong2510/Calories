package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExerciseEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("calories_burned") val caloriesBurned: Double,
    @SerialName("duration_minutes") val durationMinutes: Int = 0,
    @SerialName("created_at") val createdAt: String,
)
