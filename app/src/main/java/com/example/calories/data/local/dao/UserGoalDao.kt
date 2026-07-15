package com.example.calories.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.calories.data.local.entity.UserGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserGoalDao {

    @Query("SELECT * FROM user_goals WHERE userId = :userId LIMIT 1")
    fun observeForUser(userId: String): Flow<UserGoalEntity?>

    @Query("SELECT * FROM user_goals WHERE userId = :userId LIMIT 1")
    suspend fun getForUser(userId: String): UserGoalEntity?

    @Query("SELECT * FROM user_goals WHERE userId = :userId AND isDirty = 1")
    suspend fun getDirty(userId: String): List<UserGoalEntity>

    @Upsert
    suspend fun upsert(goal: UserGoalEntity)

    @Query("DELETE FROM user_goals WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("DELETE FROM user_goals WHERE userId = :userId AND isDirty = 0")
    suspend fun deleteSyncedForUser(userId: String)

    @Transaction
    suspend fun clearAndInsert(userId: String, goal: UserGoalEntity) {
        deleteForUser(userId)
        upsert(goal)
    }
}
