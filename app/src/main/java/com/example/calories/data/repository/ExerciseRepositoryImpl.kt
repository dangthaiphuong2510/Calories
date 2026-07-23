package com.example.calories.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.local.mapper.toEntity
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.ExercisePreset
import com.example.calories.util.DateTimeUtils
import com.example.calories.widget.WidgetRefreshNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val exerciseEntryDao: ExerciseEntryDao,
    private val supabase: SupabaseClient,
    @ApplicationContext context: Context,
    private val widgetRefreshNotifier: WidgetRefreshNotifier,
) : ExerciseRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _custom = MutableStateFlow(loadCustom())

    override fun observeExerciseEntries(userId: String): Flow<List<ExerciseEntry>> {
        return exerciseEntryDao.observeAll(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeExercisesForDate(userId: String, date: LocalDate): Flow<List<ExerciseEntry>> {
        return observeExerciseEntries(userId).map { entries ->
            entries.filter { DateTimeUtils.isSameDay(it.createdAt, date) }
        }
    }

    override fun observeCustomExercises(): Flow<List<ExercisePreset>> = _custom.asStateFlow()

    override suspend fun addExerciseEntry(
        name: String,
        caloriesBurned: Double,
        durationMinutes: Int,
        createdAt: String,
    ): ExerciseEntry {
        val userId = requireCurrentUserId()
        val entry = ExerciseEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            name = name.trim(),
            caloriesBurned = caloriesBurned,
            durationMinutes = durationMinutes,
            createdAt = createdAt,
        )
        exerciseEntryDao.upsert(entry.toEntity(isDirty = true, syncedAt = null))

        try {
            val remote = supabase.from(TABLE_NAME)
                .upsert(entry) {
                    select()
                }
                .decodeSingle<ExerciseEntry>()
            exerciseEntryDao.upsert(remote.toEntity(isDirty = false))
            widgetRefreshNotifier.notifyDataChanged()
            return remote
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync new exercise entry to Supabase", e)
            widgetRefreshNotifier.notifyDataChanged()
            return entry
        }
    }

    override suspend fun deleteExerciseEntry(id: String) {
        exerciseEntryDao.deleteById(id)
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
            Log.e(TAG, "Failed to delete exercise entry remotely id=$id", e)
        }
        widgetRefreshNotifier.notifyDataChanged()
    }

    override suspend fun addCustomExercise(
        name: String,
        calories: Int,
        durationMinutes: Int,
    ): ExercisePreset {
        val preset = ExercisePreset(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            calories = calories,
            durationMinutes = durationMinutes,
        )
        _custom.update { current ->
            val next = current + preset
            persistCustom(next)
            next
        }
        return preset
    }

    override suspend fun fetchAndSync() {
        try {
            val userId = currentUserId() ?: run {
                Log.w(TAG, "fetchAndSync skipped: no authenticated user")
                return
            }
            val dirty = exerciseEntryDao.getDirty(userId)
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                try {
                    val remote = supabase.from(TABLE_NAME)
                        .upsert(entity.toDomain()) {
                            select()
                        }
                        .decodeSingle<ExerciseEntry>()
                    exerciseEntryDao.upsert(remote.toEntity(isDirty = false))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to push dirty exercise entry id=${entity.id}", e)
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
                .decodeList<ExerciseEntry>()

            val dirtyIds = exerciseEntryDao.getDirty(userId).map { it.id }.toSet()
            if (dirtyIds.isEmpty()) {
                exerciseEntryDao.clearAndInsert(
                    userId = userId,
                    entries = remoteEntries.map { it.toEntity(isDirty = false) },
                )
            } else {
                exerciseEntryDao.deleteSyncedForUser(userId)
                exerciseEntryDao.upsertAll(
                    remoteEntries
                        .filter { it.id !in dirtyIds }
                        .map { it.toEntity(isDirty = false) },
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh exercise entries from Supabase", e)
        }
        widgetRefreshNotifier.notifyDataChanged()
    }

    private fun loadCustom(): List<ExercisePreset> {
        val raw = prefs.getString(KEY_CUSTOM, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ExercisePreset(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            calories = obj.getInt("calories"),
                            durationMinutes = obj.getInt("durationMinutes"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistCustom(items: List<ExercisePreset>) {
        val array = JSONArray()
        items.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("calories", preset.calories)
                    .put("durationMinutes", preset.durationMinutes),
            )
        }
        prefs.edit().putString(KEY_CUSTOM, array.toString()).apply()
    }

    private fun currentUserId(): String? = supabase.auth.currentUserOrNull()?.id

    private fun requireCurrentUserId(): String {
        return currentUserId() ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TAG = "ExerciseRepository"
        const val TABLE_NAME = "exercise_entries"
        const val PREFS_NAME = "exercise_store"
        const val KEY_CUSTOM = "custom_exercises"
    }
}
