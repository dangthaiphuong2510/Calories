package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodEntry
import com.example.calories.model.FoodSearchFilter
import com.example.calories.model.enums.MealType
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepositoryImpl @Inject constructor(
    private val foodEntryDao: FoodEntryDao,
    private val supabase: SupabaseClient,
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

    override suspend fun searchFoodDictionary(
        query: String,
        filter: FoodSearchFilter,
        limit: Int,
    ): List<FoodDictionaryItem> {
        val trimmed = query.trim()
        return try {
            supabase.from(DICTIONARY_TABLE)
                .select {
                    filter {
                        if (trimmed.isNotEmpty()) {
                            ilike("name", "%$trimmed%")
                        }
                        when (filter) {
                            FoodSearchFilter.ALL -> Unit
                            FoodSearchFilter.HIGH_PROTEIN -> gt("protein", 10)
                            FoodSearchFilter.LOW_CARBS -> lt("carb", 10)
                            FoodSearchFilter.LOW_FAT -> lt("fat", 3)
                        }
                    }
                    order(column = "name", order = Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<FoodDictionaryItem>()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search food_dictionary query=$trimmed filter=$filter", e)
            emptyList()
        }
    }

    override suspend fun addFoodEntry(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        mealType: MealType,
        servingGrams: Double,
        recordedAt: String,
    ): FoodEntry {
        val userId = requireCurrentUserId()
        val entry = FoodEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            mealType = mealType,
            servingGrams = servingGrams,
            createdAt = recordedAt,
        )
        foodEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))

        val savedEntry = try {
            Log.d(
                TAG,
                "Upserting food_entries id=${entry.id}, meal_type=${entry.mealType.name}, " +
                    "serving_grams=${entry.servingGrams}, calories=${entry.calories}",
            )
            val remoteEntry = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<FoodEntry>()
            foodEntryDao.upsert(remoteEntry.toEntity(isDirty = false))
            remoteEntry
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync new food entry to Supabase", e)
            entry
        }

        return savedEntry
    }

    override suspend fun updateFoodEntry(foodEntry: FoodEntry): FoodEntry {
        val userId = requireCurrentUserId()
        val entry = foodEntry.copy(userId = userId)
        foodEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))

        return try {
            Log.d(
                TAG,
                "Updating food_entries id=${entry.id}, calories=${entry.calories}, " +
                    "protein=${entry.protein}, carb=${entry.carb}, fat=${entry.fat}, " +
                    "serving_grams=${entry.servingGrams}",
            )
            val remoteEntry = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<FoodEntry>()
            foodEntryDao.upsert(remoteEntry.toEntity(isDirty = false))
            remoteEntry
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update food entry remotely id=${entry.id}", e)
            entry
        }
    }

    override suspend fun deleteFoodEntry(id: String) {
        foodEntryDao.deleteById(id)
        try {
            val userId = requireCurrentUserId()
            supabase.from(TABLE_NAME)
                .delete {
                    filter {
                        eq("id", id)
                        eq("user_id", userId)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete food entry remotely id=$id", e)
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = foodEntryDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val entry = entity.toDomain()
                    Log.d(
                        TAG,
                        "Retrying food_entries id=${entry.id}, meal_type=${entry.mealType.name}, " +
                            "serving_grams=${entry.servingGrams}, calories=${entry.calories}",
                    )
                    val remoteEntry = supabase.from(TABLE_NAME)
                        .upsert(entry) {
                            select()
                        }
                        .decodeSingle<FoodEntry>()
                    foodEntryDao.upsert(remoteEntry.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty food entry id=${entity.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndSync failed", e)
        }
    }

    override suspend fun refresh(userId: String) {
        fetchAndSync()
        try {
            val remoteEntries = supabase.from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = CREATED_AT_COLUMN, order = Order.DESCENDING)
                }
                .decodeList<FoodEntry>()

            val dirtyIds = foodEntryDao.getDirty(userId).map { it.id }.toSet()
            val remoteIds = remoteEntries.map { it.id }.toSet()

            // Merge remote into local. Never clearAndInsert — that races with a just-saved
            // entry whose Supabase row may not appear in SELECT yet and would wipe Home UI.
            foodEntryDao.upsertAll(
                remoteEntries
                    .filter { it.id !in dirtyIds }
                    .map { it.toEntity(isDirty = false) },
            )

            foodEntryDao.getAll(userId)
                .filter { !it.isDirty && it.id !in remoteIds }
                .forEach { foodEntryDao.deleteById(it.id) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh food entries from Supabase", e)
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "FoodRepository"
        const val TABLE_NAME = "food_entries"
        const val DICTIONARY_TABLE = "food_dictionary"
        const val CREATED_AT_COLUMN = "created_at"
    }
}
