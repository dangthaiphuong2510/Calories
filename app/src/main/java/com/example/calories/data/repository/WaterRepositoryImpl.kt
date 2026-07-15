package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.WaterEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.WaterEntry
import com.example.calories.util.DateTimeUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterRepositoryImpl @Inject constructor(
    private val waterEntryDao: WaterEntryDao,
    private val supabase: SupabaseClient,
) : WaterRepository {

    override fun observeWaterEntries(userId: String): Flow<List<WaterEntry>> {
        return waterEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTotalMlForDate(userId: String, date: LocalDate): Flow<Int> {
        return observeWaterEntries(userId).map { entries ->
            entries
                .filter { DateTimeUtils.isSameDay(it.createdAt, date) }
                .sumOf { it.amountMl }
        }
    }

    override suspend fun addWaterEntry(amountMl: Int, createdAt: String): WaterEntry {
        require(amountMl > 0) { "amountMl must be positive" }
        val userId = requireCurrentUserId()
        val entry = WaterEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            amountMl = amountMl,
            createdAt = createdAt,
        )
        waterEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))

        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<WaterEntry>()
            waterEntryDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync new water entry to Supabase", e)
            return entry
        }
    }

    override suspend fun removeLastForDate(date: LocalDate, amountMl: Int): Boolean {
        val userId = currentUserId() ?: return false
        val candidates = observeWaterEntries(userId).first()
            .filter {
                DateTimeUtils.isSameDay(it.createdAt, date) && it.amountMl == amountMl
            }
            .sortedByDescending { it.createdAt }
        val target = candidates.firstOrNull() ?: return false
        deleteWaterEntry(target.id)
        return true
    }

    override suspend fun deleteWaterEntry(id: String) {
        waterEntryDao.deleteById(id)
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
            Log.e(TAG, "Failed to delete water entry remotely id=$id", e)
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = waterEntryDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<WaterEntry>()
                    waterEntryDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty water entry id=${entity.id}", e)
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
                    order(column = "created_at", order = Order.DESCENDING)
                }
                .decodeList<WaterEntry>()

            val dirtyIds = waterEntryDao.getDirty(userId).map { it.id }.toSet()
            if (dirtyIds.isEmpty()) {
                waterEntryDao.clearAndInsert(
                    userId = userId,
                    entries = remoteEntries.map { it.toEntity(isDirty = false) },
                )
            } else {
                waterEntryDao.deleteSyncedForUser(userId)
                waterEntryDao.upsertAll(
                    remoteEntries
                        .filter { it.id !in dirtyIds }
                        .map { it.toEntity(isDirty = false) },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh water entries from Supabase", e)
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "WaterRepository"
        const val TABLE_NAME = "water_entries"
    }
}
