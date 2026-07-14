package com.example.calories.data.repository

import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first food log repository.
 *
 * Sync pattern:
 * 1. [observeFoodEntries] / [observeTodayCalories] read only from Room (`Flow`).
 * 2. [addFoodEntry] writes Supabase first, then upserts Room.
 * 3. Realtime remote events upsert/delete Room rows.
 * 4. [refresh] fetches remote list and [clearAndInsert]s into Room.
 */
interface FoodRepository {
    fun observeFoodEntries(userId: String): Flow<List<FoodEntry>>
    fun observeTodayCalories(userId: String, startOfDay: String, startOfTomorrow: String): Flow<Int>
    suspend fun addFoodEntry(
        name: String,
        calories: Int,
        protein: Double = 0.0,
        carb: Double = 0.0,
        fat: Double = 0.0,
        mealType: MealType = MealType.SNACK,
    ): FoodEntry
    suspend fun deleteFoodEntry(id: String)
    suspend fun refresh(userId: String)
}
