package com.example.calories.model

import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserGoal(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("target_weight") val targetWeight: Double,
    @SerialName("current_weight") val currentWeight: Double,
    val age: Int,
    val gender: Gender,
    @SerialName("height_cm") val heightCm: Double,
    @SerialName("activity_level") val activityLevel: ActivityLevel,
    @SerialName("goal_type") val goalType: GoalType,
    val tdee: Int,
    @SerialName("daily_calories") val dailyCalories: Int,
)
