package com.example.calories.data.repository

import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.data.remote.supabase.SupabaseWeightService
import com.example.calories.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepositoryImpl @Inject constructor(
    private val weightEntryDao: WeightEntryDao,
    private val remote: SupabaseWeightService,
) : WeightRepository {

    override fun observeWeightEntries(userId: String): Flow<List<WeightEntry>> {
        return weightEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addWeightEntry(weightKg: Double, recordedAt: String): WeightEntry {
        val remoteEntry = remote.addWeightEntry(weightKg, recordedAt)
        weightEntryDao.upsert(remoteEntry.toEntity())
        return remoteEntry
    }

    override suspend fun deleteWeightEntry(id: String) {
        remote.deleteWeightEntry(id)
        weightEntryDao.deleteById(id)
    }

    override suspend fun refresh(userId: String) {
        val remoteEntries = remote.getAllWeightEntries()
        weightEntryDao.clearAndInsert(
            userId = userId,
            entries = remoteEntries.map { it.toEntity() },
        )
    }
}
