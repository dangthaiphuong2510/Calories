package com.example.calories.model

import kotlinx.serialization.Serializable

@Serializable
data class FoodAnalysisResult(
    val foodName: String,
    val estimatedWeightGrams: Int,
    val calories: Int,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val ingredients: List<String> = emptyList(),
)
