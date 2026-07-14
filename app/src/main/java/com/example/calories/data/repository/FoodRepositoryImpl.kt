package com.example.calories.data.repository

import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.data.remote.supabase.SupabaseFoodService
import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepositoryImpl @Inject constructor(
    private val foodEntryDao: FoodEntryDao,
    private val remote: SupabaseFoodService,
) : FoodRepository {

    override fun observeFoodEntries(userId: String): Flow<List<FoodEntry>> {
        return foodEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTodayCalories(
        userId: String,
        startOfDay: String,
        startOfTomorrow: String,
    ): Flow<Int> {
        return foodEntryDao.observeTodayCalories(userId, startOfDay, startOfTomorrow)
    }

    override suspend fun addFoodEntry(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        mealType: MealType,
    ): FoodEntry {
        val remoteEntry = remote.addFoodEntry(
            name = name,
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            mealType = mealType,
        )
        foodEntryDao.upsert(remoteEntry.toEntity())
        return remoteEntry
    }

    override suspend fun deleteFoodEntry(id: String) {
        remote.deleteFoodEntry(id)
        foodEntryDao.deleteById(id)
    }

    override suspend fun refresh(userId: String) {
        val remoteEntries = remote.getAllFoodEntries()
        foodEntryDao.clearAndInsert(
            userId = userId,
            entries = remoteEntries.map { it.toEntity() },
        )
    }
}
