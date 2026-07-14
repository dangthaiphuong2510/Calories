package com.example.calories.data.remote.supabase

import com.example.calories.model.FridgeIngredient
import com.example.calories.model.FridgeIngredientInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFridgeService @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun addIngredient(
        name: String,
        quantity: Double,
        unit: String,
        expiryDate: String?,
    ): FridgeIngredient {
        val userId = requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .insert(
                FridgeIngredientInsert(
                    userId = userId,
                    name = name,
                    quantity = quantity,
                    unit = unit,
                    expiryDate = expiryDate,
                ),
            ) {
                select()
            }
            .decodeSingle<FridgeIngredient>()
    }

    suspend fun updateIngredient(ingredient: FridgeIngredient): FridgeIngredient {
        requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .upsert(ingredient) {
                select()
            }
            .decodeSingle<FridgeIngredient>()
    }

    suspend fun getAllIngredients(): List<FridgeIngredient> {
        requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .select {
                order(column = "name", order = Order.ASCENDING)
            }
            .decodeList<FridgeIngredient>()
    }

    suspend fun deleteIngredient(id: String) {
        requireCurrentUserId()
        supabase.from(TABLE_NAME)
            .delete {
                filter {
                    eq("id", id)
                }
            }
    }

    private fun requireCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TABLE_NAME = "fridge_ingredients"
    }
}
