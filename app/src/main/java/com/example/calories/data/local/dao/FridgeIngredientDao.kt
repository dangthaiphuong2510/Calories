package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.FridgeIngredientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FridgeIngredientDao {

    @Query("SELECT * FROM fridge_ingredients WHERE userId = :userId ORDER BY name ASC")
    fun observeAll(userId: String): Flow<List<FridgeIngredientEntity>>

    @Query("SELECT * FROM fridge_ingredients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FridgeIngredientEntity?

    @Upsert
    suspend fun upsert(ingredient: FridgeIngredientEntity)

    @Upsert
    suspend fun upsertAll(ingredients: List<FridgeIngredientEntity>)

    @Query("DELETE FROM fridge_ingredients WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM fridge_ingredients WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, ingredients: List<FridgeIngredientEntity>) {
        deleteAllForUser(userId)
        upsertAll(ingredients)
    }
}
