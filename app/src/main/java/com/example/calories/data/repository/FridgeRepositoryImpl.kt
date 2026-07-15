package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.FridgeIngredientDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.FridgeIngredient
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
class FridgeRepositoryImpl @Inject constructor(
    private val fridgeIngredientDao: FridgeIngredientDao,
    private val supabase: SupabaseClient,
) : FridgeRepository {

    override fun observeIngredients(userId: String): Flow<List<FridgeIngredient>> {
        return fridgeIngredientDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addIngredient(
        name: String,
        quantity: Double,
        unit: String,
        expiryDate: String?,
    ): FridgeIngredient {
        val userId = requireCurrentUserId()
        val ingredient = FridgeIngredient(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name,
            quantity = quantity,
            unit = unit,
            expiryDate = expiryDate,
        )
        fridgeIngredientDao.upsert(ingredient.toEntity(isDirty = true, syncedAt = null))

        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(ingredient) {
                    select()
                }
                .decodeSingle<FridgeIngredient>()
            fridgeIngredientDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync new fridge ingredient to Supabase", e)
            return ingredient
        }
    }

    override suspend fun updateIngredient(ingredient: FridgeIngredient): FridgeIngredient {
        fridgeIngredientDao.upsert(ingredient.toEntity(isDirty = true, syncedAt = null))
        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(ingredient) {
                    select()
                }
                .decodeSingle<FridgeIngredient>()
            fridgeIngredientDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync fridge ingredient update id=${ingredient.id}", e)
            return ingredient
        }
    }

    override suspend fun deleteIngredient(id: String) {
        fridgeIngredientDao.deleteById(id)
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
            Log.e(TAG, "Failed to delete fridge ingredient remotely id=$id", e)
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = fridgeIngredientDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<FridgeIngredient>()
                    fridgeIngredientDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty fridge ingredient id=${entity.id}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchAndSync failed", e)
        }
    }

    override suspend fun refresh(userId: String) {
        fetchAndSync()
        try {
            val remoteIngredients = supabase.from(TABLE_NAME)
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                    order(column = "name", order = Order.ASCENDING)
                }
                .decodeList<FridgeIngredient>()

            val dirtyIds = fridgeIngredientDao.getDirty(userId).map { it.id }.toSet()
            if (dirtyIds.isEmpty()) {
                fridgeIngredientDao.clearAndInsert(
                    userId = userId,
                    ingredients = remoteIngredients.map { it.toEntity(isDirty = false) },
                )
            } else {
                fridgeIngredientDao.deleteSyncedForUser(userId)
                fridgeIngredientDao.upsertAll(
                    remoteIngredients
                        .filter { it.id !in dirtyIds }
                        .map { it.toEntity(isDirty = false) },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh fridge ingredients from Supabase", e)
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "FridgeRepository"
        const val TABLE_NAME = "fridge_ingredients"
    }
}
