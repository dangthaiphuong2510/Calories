package com.example.calories.data.repository

import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodEntry
import com.example.calories.model.FoodSearchFilter
import com.example.calories.model.enums.MealType
import kotlinx.coroutines.flow.Flow

interface FoodRepository {
    fun observeFoodEntries(userId: String): Flow<List<FoodEntry>>
    fun observeTodayCalories(userId: String, startOfDay: String, startOfTomorrow: String): Flow<Int>
    suspend fun searchFoodDictionary(
        query: String,
        filter: FoodSearchFilter = FoodSearchFilter.ALL,
        limit: Int = 50,
    ): List<FoodDictionaryItem>
    suspend fun addFoodEntry(
        name: String,
        calories: Int,
        protein: Double = 0.0,
        carb: Double = 0.0,
        fat: Double = 0.0,
        mealType: MealType = MealType.SNACKS,
        servingGrams: Double = 100.0,
        recordedAt: String = com.example.calories.util.DateTimeUtils.nowIso(),
    ): FoodEntry
    suspend fun updateFoodEntry(foodEntry: FoodEntry): FoodEntry
    suspend fun deleteFoodEntry(id: String)
    /** Push local rows with `isDirty = true` to Supabase. */
    suspend fun fetchAndSync()
    /** Push dirty rows, then pull remote into Room. */
    suspend fun refresh(userId: String)
}
