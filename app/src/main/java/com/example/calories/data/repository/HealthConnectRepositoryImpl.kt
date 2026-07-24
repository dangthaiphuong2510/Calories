package com.example.calories.data.repository

import android.util.Log
import com.example.calories.data.auth.ActiveUserIdProvider
import com.example.calories.data.health.HealthConnectManager
import com.example.calories.data.local.dao.DailyStepsDao
import com.example.calories.data.local.entity.DailyStepsEntity
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.util.StepCalorieCalculator
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectRepositoryImpl @Inject constructor(
    private val dailyStepsDao: DailyStepsDao,
    private val healthConnectManager: HealthConnectManager,
    private val activeUserIdProvider: ActiveUserIdProvider,
    private val supabase: SupabaseClient,
) : HealthConnectRepository {

    override fun observeStepsForDate(userId: String, date: LocalDate): Flow<Long?> {
        return dailyStepsDao.observeForDate(date.toString(), userId).map { it?.stepCount }
    }

    override suspend fun syncFromHealthConnect() {
        Log.d(SYNC_TAG, "syncFromHealthConnect: starting")
        if (!healthConnectManager.isAvailable) {
            Log.d(SYNC_TAG, "syncFromHealthConnect: skipped, Health Connect unavailable")
            syncDailySteps()
            return
        }
        if (!healthConnectManager.hasStepsPermission()) {
            Log.d(SYNC_TAG, "syncFromHealthConnect: skipped, steps permission not granted")
            syncDailySteps()
            return
        }

        val userId = activeUserIdProvider.get() ?: run {
            Log.w(SYNC_TAG, "syncFromHealthConnect: skipped, no authenticated user")
            return
        }

        runCatching { healthConnectManager.readDailyStepsFromYesterday() }
            .onSuccess { dailyTotals ->
                Log.d(SYNC_TAG, "syncFromHealthConnect: read ${dailyTotals.size} day(s) from Health Connect")
                val now = System.currentTimeMillis()
                val entities = dailyTotals.map { (date, stepCount) ->
                    val existing = dailyStepsDao.getForDate(date, userId)
                    DailyStepsEntity(
                        date = date,
                        userId = userId,
                        stepCount = stepCount,
                        caloriesBurned = StepCalorieCalculator.caloriesBurnedFromSteps(stepCount),
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                        isDirty = true,
                    )
                }
                if (entities.isNotEmpty()) {
                    dailyStepsDao.upsertAll(entities)
                    Log.d(SYNC_TAG, "syncFromHealthConnect: saved ${entities.size} row(s) to Room")
                }
                syncDailySteps()
            }
            .onFailure { error ->
                Log.e(SYNC_TAG, "syncFromHealthConnect: Health Connect read failed", error)
                syncDailySteps()
            }
    }

    override suspend fun syncDailySteps() {
        Log.d(SYNC_TAG, "syncDailySteps: starting")
        try {
            supabase.auth.awaitInitialization()
            val hasSession = supabase.auth.currentSessionOrNull() != null
            Log.d(SYNC_TAG, "syncDailySteps: auth initialized, hasSession=$hasSession")

            val userId = activeUserIdProvider.get() ?: run {
                Log.w(SYNC_TAG, "syncDailySteps: skipped, no authenticated user")
                return
            }

            val dirty = dailyStepsDao.getDirty(userId)
            Log.d(SYNC_TAG, "syncDailySteps: found ${dirty.size} dirty row(s) for userId=$userId")
            if (dirty.isEmpty()) return

            for (entity in dirty) {
                val payload = entity.toDomain()
                Log.d(
                    SYNC_TAG,
                    "syncDailySteps: upserting date=${payload.date} " +
                        "stepCount=${payload.stepCount} caloriesBurned=${payload.caloriesBurned} " +
                        "createdAt=${payload.createdAt} updatedAt=${payload.updatedAt}",
                )
                try {
                    supabase.from(TABLE_NAME).upsert(payload) {
                        onConflict = "date,user_id"
                    }
                    Log.d(SYNC_TAG, "syncDailySteps: upsert success date=${entity.date}")
                    dailyStepsDao.upsert(entity.copy(isDirty = false))
                    Log.d(SYNC_TAG, "syncDailySteps: marked clean in Room date=${entity.date}")
                } catch (e: Exception) {
                    Log.e(
                        SYNC_TAG,
                        "syncDailySteps: upsert failed date=${entity.date} message=${e.message}",
                        e,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(SYNC_TAG, "syncDailySteps: fatal error message=${e.message}", e)
        }
    }

    private companion object {
        const val SYNC_TAG = "DailyStepsSync"
        const val TABLE_NAME = "daily_steps"
    }
}
