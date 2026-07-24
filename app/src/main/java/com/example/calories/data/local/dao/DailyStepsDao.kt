package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.calories.data.local.entity.DailyStepsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepsDao {

    @Query("SELECT * FROM daily_steps WHERE date = :date AND userId = :userId LIMIT 1")
    fun observeForDate(date: String, userId: String): Flow<DailyStepsEntity?>

    @Query("SELECT * FROM daily_steps WHERE date = :date AND userId = :userId LIMIT 1")
    suspend fun getForDate(date: String, userId: String): DailyStepsEntity?

    @Query("SELECT * FROM daily_steps WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<DailyStepsEntity>

    @Upsert
    suspend fun upsert(entity: DailyStepsEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DailyStepsEntity>)
}
