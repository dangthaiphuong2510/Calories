package com.example.calories.di

import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthRepositoryImpl
import com.example.calories.data.repository.ExerciseRepository
import com.example.calories.data.repository.ExerciseRepositoryImpl
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.FoodRepositoryImpl
import com.example.calories.data.repository.FridgeRepository
import com.example.calories.data.repository.FridgeRepositoryImpl
import com.example.calories.data.repository.ProfileRepository
import com.example.calories.data.repository.ProfileRepositoryImpl
import com.example.calories.data.repository.RecipeRepository
import com.example.calories.data.repository.RecipeRepositoryImpl
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.data.repository.UserGoalsRepositoryImpl
import com.example.calories.data.repository.WaterRepository
import com.example.calories.data.repository.WaterRepositoryImpl
import com.example.calories.data.repository.WeightRepository
import com.example.calories.data.repository.WeightRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFoodRepository(impl: FoodRepositoryImpl): FoodRepository

    @Binds
    @Singleton
    abstract fun bindUserGoalsRepository(impl: UserGoalsRepositoryImpl): UserGoalsRepository

    @Binds
    @Singleton
    abstract fun bindWeightRepository(impl: WeightRepositoryImpl): WeightRepository

    @Binds
    @Singleton
    abstract fun bindFridgeRepository(impl: FridgeRepositoryImpl): FridgeRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWaterRepository(impl: WaterRepositoryImpl): WaterRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindRecipeRepository(impl: RecipeRepositoryImpl): RecipeRepository
}
