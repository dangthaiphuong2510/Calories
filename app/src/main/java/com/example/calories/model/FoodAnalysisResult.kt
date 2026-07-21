package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodAnalysisResult(
    @SerialName("is_food") val isFood: Boolean = true,
    @SerialName("food_name") val foodName: String,
    val calories: Int,
    val estimatedWeightGrams: Int = 0,
    val proteinGrams: Float = 0f,
    val carbsGrams: Float = 0f,
    val fatGrams: Float = 0f,
    val ingredients: List<String> = emptyList(),
) {
    val isFoodDetected: Boolean
        get() = isFood && !foodName.equals("None", ignoreCase = true)
}
