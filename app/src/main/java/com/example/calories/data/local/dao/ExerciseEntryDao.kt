package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.ExerciseEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseEntryDao {

    @Query("SELECT * FROM exercise_entries WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeAll(userId: String): Flow<List<ExerciseEntryEntity>>

    @Query("SELECT * FROM exercise_entries WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntryEntity?

    @Query("SELECT * FROM exercise_entries WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<ExerciseEntryEntity>

    @Upsert
    suspend fun upsert(entry: ExerciseEntryEntity)

    @Upsert
    suspend fun upsertAll(entries: List<ExerciseEntryEntity>)

    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM exercise_entries WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM exercise_entries WHERE userId = :userId AND isDirty = 0")
    suspend fun deleteSyncedForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, entries: List<ExerciseEntryEntity>) {
        deleteAllForUser(userId)
        upsertAll(entries)
    }
}
