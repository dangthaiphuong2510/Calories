package com.example.calories.data.preferences

import android.content.Context
import com.example.calories.data.local.CaloriesDatabase
import com.example.calories.notifications.ReminderIds
import com.example.calories.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDataWiper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: CaloriesDatabase,
    private val appPreferences: AppPreferences,
    private val authDataStore: AuthDataStore,
    private val insightPreferences: InsightPreferences,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        appPreferences.clear()
        authDataStore.clearLoginState()
        context.getSharedPreferences(NotificationPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        insightPreferences.clear()
        context.getSharedPreferences(EXERCISE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        cancelAllReminders()
    }

    private fun cancelAllReminders() {
        reminderScheduler.cancelAlarm(ReminderIds.MEAL_BREAKFAST)
        reminderScheduler.cancelAlarm(ReminderIds.MEAL_LUNCH)
        reminderScheduler.cancelAlarm(ReminderIds.MEAL_DINNER)
        reminderScheduler.cancelAlarm(ReminderIds.MEAL_SNACKS)
        repeat(ReminderIds.MAX_SCHEDULE_SLOTS) { index ->
            reminderScheduler.cancelAlarm(ReminderIds.water(index))
            reminderScheduler.cancelAlarm(ReminderIds.workout(index))
        }
    }

    private companion object {
        const val EXERCISE_PREFS_NAME = "exercise_store"
    }
}
