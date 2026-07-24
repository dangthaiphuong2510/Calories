package com.example.calories.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.calories.R
import com.example.calories.data.auth.ActiveUserIdProvider
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WaterEntryDao // <-- Thêm DAO này làm fallback
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.preferences.InsightPreferences
import com.example.calories.data.repository.WaterRepository
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CaloriesWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeUserIdProvider: ActiveUserIdProvider,
    private val waterRepository: Provider<WaterRepository>,
    private val waterEntryDao: WaterEntryDao,
    private val foodEntryDao: FoodEntryDao,
    private val userGoalDao: UserGoalDao,
    private val weightEntryDao: WeightEntryDao,
    private val exerciseEntryDao: ExerciseEntryDao,
    private val insightPreferences: InsightPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun scheduleRefresh() {
        scope.launch { updateAll() }
    }

    suspend fun updateAll() = withContext(Dispatchers.IO) {
        val userId = activeUserIdProvider.get()
            ?: userGoalDao.getAnyUserId()
            ?: waterEntryDao.getLatestUserId()

        val today = DateTimeUtils.today()
        val (startOfDay, startOfTomorrow) = DateTimeUtils.dayRange(today)

        val snapshot = if (userId.isNullOrEmpty()) {
            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = false,
                goal = null,
                todayFoods = emptyList(),
                allFoods = emptyList(),
                weights = emptyList(),
                todayExercises = emptyList(),
                dismissedIds = emptySet(),
                waterIntakeMl = 0,
                today = today,
            )
        } else {
            insightPreferences.ensureCurrentWeek()
            val goal = userGoalDao.getForUser(userId)?.toDomain()
            val todayFoods = foodEntryDao.getForDay(userId, startOfDay, startOfTomorrow)
                .map { it.toDomain() }
            val allFoods = foodEntryDao.getAll(userId).map { it.toDomain() }
            val weights = weightEntryDao.getAll(userId).map { it.toDomain() }
            val todayExercises = exerciseEntryDao.getAll(userId)
                .map { it.toDomain() }
                .filter { DateTimeUtils.isSameDay(it.createdAt, today) }

            val waterIntakeMl = waterRepository.get().getTotalMlForDate(userId, today)

            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = true,
                goal = goal,
                todayFoods = todayFoods,
                allFoods = allFoods,
                weights = weights,
                todayExercises = todayExercises,
                dismissedIds = insightPreferences.dismissedIds.value,
                waterIntakeMl = waterIntakeMl,
                today = today,
            )
        }

        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CaloriesHomeWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return@withContext

        val openIntent = CaloriesHomeWidgetProvider.createOpenAppPendingIntent(context)
        val addWaterIntent = CaloriesHomeWidgetProvider.createAddWaterPendingIntent(context)

        ids.forEach { id ->
            val remoteViews = CaloriesWidgetRenderer.render(context, snapshot)

            bindWidgetClickIntents(
                remoteViews = remoteViews,
                openIntent = openIntent,
                addWaterIntent = addWaterIntent
            )
            manager.updateAppWidget(id, remoteViews)
        }
    }

    private fun bindWidgetClickIntents(
        remoteViews: android.widget.RemoteViews,
        openIntent: android.app.PendingIntent,
        addWaterIntent: android.app.PendingIntent,
    ) {
        remoteViews.setOnClickPendingIntent(R.id.sectionProgress, openIntent)
        remoteViews.setOnClickPendingIntent(R.id.sectionInsight, openIntent)
        remoteViews.setOnClickPendingIntent(R.id.tvWidgetMessage, openIntent)
        remoteViews.setOnClickPendingIntent(R.id.btnAddWater, addWaterIntent)
    }
}