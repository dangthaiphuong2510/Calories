package com.example.calories.data.repository

import com.example.calories.model.FridgeIngredient
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first fridge ingredients repository.
 *
 * Sync pattern:
 * 1. [observeIngredients] reads from Room (`Flow`).
 * 2. [addIngredient] / [updateIngredient] write Supabase first, then upsert Room.
 * 3. Realtime remote events upsert/delete Room rows.
 * 4. [refresh] fetches remote list and clearAndInserts into Room.
 */
interface FridgeRepository {
    fun observeIngredients(userId: String): Flow<List<FridgeIngredient>>
    suspend fun addIngredient(
        name: String,
        quantity: Double,
        unit: String,
        expiryDate: String? = null,
    ): FridgeIngredient
    suspend fun updateIngredient(ingredient: FridgeIngredient): FridgeIngredient
    suspend fun deleteIngredient(id: String)
    suspend fun refresh(userId: String)
}
