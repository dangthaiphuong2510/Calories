package com.example.calories.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodAnalysisResult(
    val name: String,
    val calories: Int,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
)
