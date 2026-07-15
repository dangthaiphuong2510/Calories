package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.WaterEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaterEntryDao {

    @Query("SELECT * FROM water_entries WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<WaterEntryEntity>>

    @Query("SELECT * FROM water_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WaterEntryEntity?

    @Query("SELECT * FROM water_entries WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<WaterEntryEntity>

    @Upsert
    suspend fun upsert(entry: WaterEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<WaterEntryEntity>)

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM water_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM water_entries WHERE userId = :userId AND isDirty = 0")
    suspend fun deleteSyncedForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, entries: List<WaterEntryEntity>) {
        deleteAllForUser(userId)
        upsertAll(entries)
    }
}
