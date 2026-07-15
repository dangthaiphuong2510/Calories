package com.example.calories.data.repository

import com.example.calories.model.FridgeIngredient
import kotlinx.coroutines.flow.Flow

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
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
