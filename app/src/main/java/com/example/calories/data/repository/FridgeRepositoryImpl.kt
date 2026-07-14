package com.example.calories.data.repository

import com.example.calories.data.local.dao.FridgeIngredientDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.data.remote.supabase.SupabaseFridgeService
import com.example.calories.model.FridgeIngredient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FridgeRepositoryImpl @Inject constructor(
    private val fridgeIngredientDao: FridgeIngredientDao,
    private val remote: SupabaseFridgeService,
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
        val remoteIngredient = remote.addIngredient(name, quantity, unit, expiryDate)
        fridgeIngredientDao.upsert(remoteIngredient.toEntity())
        return remoteIngredient
    }

    override suspend fun updateIngredient(ingredient: FridgeIngredient): FridgeIngredient {
        val remoteIngredient = remote.updateIngredient(ingredient)
        fridgeIngredientDao.upsert(remoteIngredient.toEntity())
        return remoteIngredient
    }

    override suspend fun deleteIngredient(id: String) {
        remote.deleteIngredient(id)
        fridgeIngredientDao.deleteById(id)
    }

    override suspend fun refresh(userId: String) {
        val remoteIngredients = remote.getAllIngredients()
        fridgeIngredientDao.clearAndInsert(
            userId = userId,
            ingredients = remoteIngredients.map { it.toEntity() },
        )
    }
}
