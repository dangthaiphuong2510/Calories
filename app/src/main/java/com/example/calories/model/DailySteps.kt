package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DailySteps(
    val date: String,
    @SerialName("user_id") val userId: String,
    @SerialName("step_count") val stepCount: Long,
    @SerialName("calories_burned") val caloriesBurned: Double,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_dirty") val isDirty: Boolean = false,
)
