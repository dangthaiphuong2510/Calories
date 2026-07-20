package com.example.calories.data.remote.supabase

import com.example.calories.model.FoodEntry
import com.example.calories.model.FoodEntryInsert
import com.example.calories.model.enums.MealType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFoodService @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun addFoodEntry(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        mealType: MealType,
        servingGrams: Double = 100.0,
    ): FoodEntry {
        val userId = requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .insert(
                FoodEntryInsert(
                    userId = userId,
                    name = name,
                    calories = calories,
                    protein = protein,
                    carb = carb,
                    fat = fat,
                    mealType = mealType,
                    servingGrams = servingGrams,
                ),
            ) {
                select()
            }
            .decodeSingle<FoodEntry>()
    }

    suspend fun getAllFoodEntries(): List<FoodEntry> {
        val userId = requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .select {
                filter {
                    eq("user_id", userId)
                }
                order(column = CREATED_AT_COLUMN, order = Order.DESCENDING)
            }
            .decodeList<FoodEntry>()
    }

    suspend fun deleteFoodEntry(id: String) {
        val userId = requireCurrentUserId()
        supabase.from(TABLE_NAME)
            .delete {
                filter {
                    eq("id", id)
                    eq("user_id", userId)
                }
            }
    }

    private fun requireCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TABLE_NAME = "food_entries"
        const val CREATED_AT_COLUMN = "created_at"
    }
}
