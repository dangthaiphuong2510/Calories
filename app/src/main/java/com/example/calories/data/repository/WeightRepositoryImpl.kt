package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.WeightEntry
import com.example.calories.util.DateTimeUtils
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepositoryImpl @Inject constructor(
    private val weightEntryDao: WeightEntryDao,
    private val supabase: SupabaseClient,
) : WeightRepository {

    override fun observeWeightEntries(userId: String): Flow<List<WeightEntry>> {
        return weightEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addWeightEntry(weightKg: Double, recordedAt: String): WeightEntry {
        val userId = requireCurrentUserId()
        val entry = WeightEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            weightKg = weightKg,
            recordedAt = recordedAt,
        )
        return persistAndSync(entry)
    }

    override suspend fun upsertWeightForDate(weightKg: Double, date: LocalDate): WeightEntry {
        val userId = requireCurrentUserId()
        val sameDay = weightEntryDao.getAll(userId)
            .filter { DateTimeUtils.isSameDay(it.recordedAt, date) }
            .sortedByDescending { it.recordedAt }

        val keep = sameDay.firstOrNull()
        // Collapse duplicates created by the old per-tap insert stepper.
        sameDay.drop(1).forEach { duplicate ->
            runCatching { deleteWeightEntry(duplicate.id) }
        }

        val entry = WeightEntry(
            id = keep?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            weightKg = weightKg,
            // Bump timestamp so Progress "current" resolves to this save.
            recordedAt = DateTimeUtils.atNowOnDateIso(date),
        )
        return persistAndSync(entry)
    }

    override suspend fun deleteWeightEntry(id: String) {
        weightEntryDao.deleteById(id)
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
            Log.e(TAG, "Failed to delete weight entry remotely id=$id", e)
        }
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = weightEntryDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<WeightEntry>()
                    weightEntryDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty weight entry id=${entity.id}", e)
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
                    order(column = "recorded_at", order = Order.ASCENDING)
                }
                .decodeList<WeightEntry>()

            val dirtyIds = weightEntryDao.getDirty(userId).map { it.id }.toSet()
            if (dirtyIds.isEmpty()) {
                weightEntryDao.clearAndInsert(
                    userId = userId,
                    entries = remoteEntries.map { it.toEntity(isDirty = false) },
                )
            } else {
                weightEntryDao.deleteSyncedForUser(userId)
                weightEntryDao.upsertAll(
                    remoteEntries
                        .filter { it.id !in dirtyIds }
                        .map { it.toEntity(isDirty = false) },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh weight entries from Supabase", e)
        }
    }

    private suspend fun persistAndSync(entry: WeightEntry): WeightEntry {
        weightEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))

        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<WeightEntry>()
            weightEntryDao.upsert(remote.toEntity(isDirty = false))
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync weight entry to Supabase id=${entry.id}", e)
            return entry
        }
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "WeightRepository"
        const val TABLE_NAME = "weight_entries"
    }
}
