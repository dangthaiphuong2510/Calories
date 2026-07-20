package com.example.calories.data

import com.example.calories.model.FoodEntry
import com.example.calories.model.FoodEntryInsert
import com.example.calories.model.enums.MealType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.time.LocalDate
import java.time.ZoneId

class SupabaseService(
    private val supabase: SupabaseClient = SupabaseClientProvider.client,
) {

    suspend fun addFoodEntry(name: String, calories: Int): FoodEntry {
        val userId = requireCurrentUserId()

        return supabase.from(TABLE_NAME)
            .insert(
                FoodEntryInsert(
                    userId = userId,
                    name = name,
                    calories = calories,
                    protein = 0.0,
                    carb = 0.0,
                    fat = 0.0,
                    mealType = MealType.SNACKS,
                    servingGrams = 100.0,
                ),
            ) {
                select()
            }
            .decodeSingle<FoodEntry>()
    }

    suspend fun getAllFoodEntries(): List<FoodEntry> {
        requireCurrentUserId()

        return supabase.from(TABLE_NAME)
            .select {
                order(column = CREATED_AT_COLUMN, order = Order.DESCENDING)
            }
            .decodeList<FoodEntry>()
    }

    suspend fun getTotalCaloriesToday(): Int {
        requireCurrentUserId()

        val (startOfDay, startOfTomorrow) = todayRange()

        val entries = supabase.from(TABLE_NAME)
            .select {
                filter {
                    gte(CREATED_AT_COLUMN, startOfDay)
                    lt(CREATED_AT_COLUMN, startOfTomorrow)
                }
            }
            .decodeList<FoodEntry>()

        return entries.sumOf { it.calories }
    }

    private fun requireCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private fun todayRange(): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant().toString()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toString()
        return startOfDay to startOfTomorrow
    }

    private companion object {
        const val TABLE_NAME = "food_entries"
        const val CREATED_AT_COLUMN = "created_at"
    }
}
