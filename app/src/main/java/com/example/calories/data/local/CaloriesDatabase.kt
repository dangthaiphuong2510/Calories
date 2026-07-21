package com.example.calories.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.dao.FavoriteFoodDao
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.FridgeIngredientDao
import com.example.calories.data.local.dao.ProfileDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WaterEntryDao
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.entity.ExerciseEntryEntity
import com.example.calories.data.local.entity.FavoriteFoodEntity
import com.example.calories.data.local.entity.FoodEntryEntity
import com.example.calories.data.local.entity.FridgeIngredientEntity
import com.example.calories.data.local.entity.ProfileEntity
import com.example.calories.data.local.entity.UserGoalEntity
import com.example.calories.data.local.entity.WaterEntryEntity
import com.example.calories.data.local.entity.WeightEntryEntity

@Database(
    entities = [
        FoodEntryEntity::class,
        UserGoalEntity::class,
        WeightEntryEntity::class,
        FridgeIngredientEntity::class,
        ExerciseEntryEntity::class,
        WaterEntryEntity::class,
        ProfileEntity::class,
        FavoriteFoodEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class CaloriesDatabase : RoomDatabase() {
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun userGoalDao(): UserGoalDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun fridgeIngredientDao(): FridgeIngredientDao
    abstract fun exerciseEntryDao(): ExerciseEntryDao
    abstract fun waterEntryDao(): WaterEntryDao
    abstract fun profileDao(): ProfileDao
    abstract fun favoriteFoodDao(): FavoriteFoodDao
}
