package com.example.calories.model

import com.example.calories.model.enums.MealType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Domain / network model for `food_entries` on Supabase.
 */
@Serializable
data class FoodEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val calories: Int,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    @SerialName("meal_type") val mealType: MealType = MealType.SNACK,
    @SerialName("serving_grams") val servingGrams: Double = 100.0,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FoodEntryInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val calories: Int,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    @SerialName("meal_type") val mealType: MealType = MealType.SNACK,
)
