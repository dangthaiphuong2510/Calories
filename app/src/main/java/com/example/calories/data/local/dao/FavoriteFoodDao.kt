package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.calories.data.local.entity.FavoriteFoodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteFoodDao {

    @Query("SELECT foodId FROM favorite_foods WHERE userId = :userId")
    fun observeFavoriteIds(userId: String): Flow<List<String>>

    @Query("SELECT * FROM favorite_foods WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<FavoriteFoodEntity>>

    @Query("SELECT * FROM favorite_foods WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getAll(userId: String): List<FavoriteFoodEntity>

    @Upsert
    suspend fun upsert(entity: FavoriteFoodEntity)

    @Query("DELETE FROM favorite_foods WHERE userId = :userId AND foodId = :foodId")
    suspend fun delete(userId: String, foodId: String)
}
