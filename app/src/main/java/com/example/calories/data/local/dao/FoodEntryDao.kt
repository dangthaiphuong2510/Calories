package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Query("SELECT * FROM food_entries WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<FoodEntryEntity>>

    @Query(
        """
        SELECT * FROM food_entries
        WHERE userId = :userId AND createdAt >= :startOfDay AND createdAt < :startOfTomorrow
        ORDER BY createdAt DESC
        """
    )
    fun observeToday(userId: String, startOfDay: String, startOfTomorrow: String): Flow<List<FoodEntryEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(calories), 0) FROM food_entries
        WHERE userId = :userId AND createdAt >= :startOfDay AND createdAt < :startOfTomorrow
        """
    )
    fun observeTodayCalories(userId: String, startOfDay: String, startOfTomorrow: String): Flow<Int>

    @Query(
        """
        SELECT * FROM food_entries
        WHERE userId = :userId AND createdAt >= :startOfDay AND createdAt < :startOfTomorrow
        """
    )
    suspend fun getForDay(
        userId: String,
        startOfDay: String,
        startOfTomorrow: String,
    ): List<FoodEntryEntity>

    @Query("SELECT * FROM food_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FoodEntryEntity?

    @Query("SELECT * FROM food_entries WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<FoodEntryEntity>

    @Query("SELECT * FROM food_entries WHERE userId = :userId")
    suspend fun getAll(userId: String): List<FoodEntryEntity>

    @Upsert
    suspend fun upsert(entry: FoodEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<FoodEntryEntity>)

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM food_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM food_entries WHERE userId = :userId AND isDirty = 0")
    suspend fun deleteSyncedForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, entries: List<FoodEntryEntity>) {
        deleteAllForUser(userId)
        upsertAll(entries)
    }
}
