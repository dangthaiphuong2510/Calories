package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.WaterEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.di.ApplicationScope
import com.example.calories.model.WaterEntry
import com.example.calories.util.DateTimeUtils
import com.example.calories.widget.WidgetRefresher
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaterRepositoryImpl @Inject constructor(
    private val waterEntryDao: WaterEntryDao,
    private val supabase: SupabaseClient,
    private val widgetRefresher: WidgetRefresher,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : WaterRepository {

    override fun observeWaterEntries(userId: String): Flow<List<WaterEntry>> {
        return waterEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeTotalMlForDate(userId: String, date: LocalDate): Flow<Int> {
        val (startInclusive, endExclusive) = DateTimeUtils.dayRange(date)
        return waterEntryDao.observeTotalMlForDay(userId, startInclusive, endExclusive)
    }

    override suspend fun getTotalMlForDate(userId: String, date: LocalDate): Int {
        return observeTotalMlForDate(userId, date).first()
    }

    override suspend fun addWaterEntry(
        amountMl: Int,
        createdAt: String,
        userIdOverride: String?,
    ): WaterEntry {
        require(amountMl > 0) { "amountMl must be positive" }
        val userId = userIdOverride ?: requireCurrentUserId()
        val entry = WaterEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            amountMl = amountMl,
            createdAt = createdAt,
        )
        waterEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))
        widgetRefresher.notifyDataChanged()
        applicationScope.launch { pushEntryToRemote(entry) }
        return entry
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
        widgetRefresher.notifyDataChanged()
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
            supabase.auth.awaitInitialization()
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
        widgetRefresher.notifyDataChanged()
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private suspend fun pushEntryToRemote(entry: WaterEntry) {
        try {
            supabase.auth.awaitInitialization()
            if (supabase.auth.currentSessionOrNull() == null) {
                Log.w(TAG, "Saved water locally; remote sync deferred until session is available")
                return
            }
            val remote = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<WaterEntry>()
            waterEntryDao.upsert(remote.toEntity(isDirty = false))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync new water entry to Supabase", e)
        }
    }

    private companion object {
        const val TAG = "WaterRepository"
        const val TABLE_NAME = "water_entries"
    }
}
