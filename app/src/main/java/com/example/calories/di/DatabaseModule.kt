package com.example.calories.di

import android.content.Context
import androidx.room.Room
import com.example.calories.data.local.CaloriesDatabase
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.dao.FavoriteFoodDao
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.FridgeIngredientDao
import com.example.calories.data.local.dao.ProfileDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WaterEntryDao
import com.example.calories.data.local.dao.WeightEntryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCaloriesDatabase(
        @ApplicationContext context: Context,
    ): CaloriesDatabase {
        return Room.databaseBuilder(
            context,
            CaloriesDatabase::class.java,
            DATABASE_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideFoodEntryDao(database: CaloriesDatabase): FoodEntryDao =
        database.foodEntryDao()

    @Provides
    fun provideUserGoalDao(database: CaloriesDatabase): UserGoalDao =
        database.userGoalDao()

    @Provides
    fun provideWeightEntryDao(database: CaloriesDatabase): WeightEntryDao =
        database.weightEntryDao()

    @Provides
    fun provideFridgeIngredientDao(database: CaloriesDatabase): FridgeIngredientDao =
        database.fridgeIngredientDao()

    @Provides
    fun provideExerciseEntryDao(database: CaloriesDatabase): ExerciseEntryDao =
        database.exerciseEntryDao()

    @Provides
    fun provideWaterEntryDao(database: CaloriesDatabase): WaterEntryDao =
        database.waterEntryDao()

    @Provides
    fun provideProfileDao(database: CaloriesDatabase): ProfileDao =
        database.profileDao()

    @Provides
    fun provideFavoriteFoodDao(database: CaloriesDatabase): FavoriteFoodDao =
        database.favoriteFoodDao()

    private const val DATABASE_NAME = "calories.db"
}
