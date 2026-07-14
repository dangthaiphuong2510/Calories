package com.example.calories.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.FridgeIngredientDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.entity.FoodEntryEntity
import com.example.calories.data.local.entity.FridgeIngredientEntity
import com.example.calories.data.local.entity.UserGoalEntity
import com.example.calories.data.local.entity.WeightEntryEntity

@Database(
    entities = [
        FoodEntryEntity::class,
        UserGoalEntity::class,
        WeightEntryEntity::class,
        FridgeIngredientEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class CaloriesDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun userGoalDao(): UserGoalDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun fridgeIngredientDao(): FridgeIngredientDao
}
