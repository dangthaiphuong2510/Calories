package com.example.calories.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.example.calories.R
import com.example.calories.data.local.dao.ExerciseEntryDao
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.local.dao.WeightEntryDao
import com.example.calories.data.local.mapper.toDomain
import com.example.calories.data.preferences.InsightPreferences
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaloriesWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
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
        val userId = supabase.auth.currentUserOrNull()?.id
        val today = DateTimeUtils.today()
        val (startOfDay, startOfTomorrow) = DateTimeUtils.dayRange(today)

        val snapshot = if (userId == null) {
            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = false,
                goal = null,
                todayFoods = emptyList(),
                allFoods = emptyList(),
                weights = emptyList(),
                todayExercises = emptyList(),
                dismissedIds = emptySet(),
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

            CaloriesWidgetSnapshotBuilder.build(
                isSignedIn = true,
                goal = goal,
                todayFoods = todayFoods,
                allFoods = allFoods,
                weights = weights,
                todayExercises = todayExercises,
                dismissedIds = insightPreferences.dismissedIds.value,
                today = today,
            )
        }

        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CaloriesHomeWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return@withContext

        val openIntent = CaloriesHomeWidgetProvider.createOpenAppPendingIntent(context)
        ids.forEach { id ->
            val remoteViews = CaloriesWidgetRenderer.render(context, snapshot)
            remoteViews.setOnClickPendingIntent(R.id.widgetRoot, openIntent)
            manager.updateAppWidget(id, remoteViews)
        }
    }
}
