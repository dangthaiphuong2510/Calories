package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FoodDictionaryItem(
    val id: String,
    val name: String,
    val calories: Long? = null,
    val protein: Double? = null,
    val carb: Double? = null,
    val fat: Double? = null,
) {
    val caloriesInt: Int get() = calories?.toInt() ?: 0
    val proteinGrams: Double get() = protein ?: 0.0
    val carbGrams: Double get() = carb ?: 0.0
    val fatGrams: Double get() = fat ?: 0.0
}

enum class FoodSearchFilter {
    ALL,
    HIGH_PROTEIN,
    LOW_CARBS,
    LOW_FAT,
}

enum class FoodSearchTab {
    RECENT,
    FAVORITES,
}
