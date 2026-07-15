package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.WeightEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Query("SELECT * FROM weight_entries WHERE userId = :userId ORDER BY recordedAt ASC")
    fun observeAll(userId: String): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries WHERE userId = :userId ORDER BY recordedAt ASC")
    suspend fun getAll(userId: String): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<WeightEntryEntity>

    @Upsert
    suspend fun upsert(entry: WeightEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<WeightEntryEntity>)

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM weight_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM weight_entries WHERE userId = :userId AND isDirty = 0")
    suspend fun deleteSyncedForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, entries: List<WeightEntryEntity>) {
        deleteAllForUser(userId)
        upsertAll(entries)
    }
}
