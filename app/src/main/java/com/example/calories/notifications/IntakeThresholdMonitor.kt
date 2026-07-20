package com.example.calories.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.calories.R
import com.example.calories.data.local.dao.FoodEntryDao
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.preferences.NotificationPreferences
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_CALORIES
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_CARBS
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_FAT
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_PROTEIN
import com.example.calories.util.CalorieCalculator
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checks today's intake totals after a meal is saved and posts system notifications
 * when calorie or macro targets are exceeded. Uses the same [ReminderScheduler.postNotification]
 * path as water/meal reminders, with once-per-day deduplication via [NotificationPreferences].
 */
@Singleton
class IntakeThresholdMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodEntryDao: FoodEntryDao,
    private val userGoalDao: UserGoalDao,
    private val notificationPreferences: NotificationPreferences,
    private val reminderScheduler: ReminderScheduler,
) {

    /** Call immediately after a food entry is persisted to Room. */
    suspend fun checkAfterFoodLogged(userId: String) {
        val settings = notificationPreferences.load()
        if (!settings.intakeWarningsEnabled) return

        val goal = userGoalDao.getForUser(userId) ?: return
        val dailyGoal = goal.dailyCalories
        if (dailyGoal <= 0) return

        val (startOfDay, startOfTomorrow) = DateTimeUtils.todayRange()
        val todayEntries = foodEntryDao.getForDay(userId, startOfDay, startOfTomorrow)
        val totalCalories = todayEntries.sumOf { it.calories }
        val totalProtein = todayEntries.sumOf { it.protein }
        val totalCarbs = todayEntries.sumOf { it.carb }
        val totalFat = todayEntries.sumOf { it.fat }

        val macros = CalorieCalculator.macroTargetsFor(dailyGoal)
        val todayKey = DateTimeUtils.today().toString()
        val title = context.getString(R.string.goal_exceeded_title)

        if (totalCalories > dailyGoal) {
            notifyOnce(
                metricKey = METRIC_CALORIES,
                notificationId = ReminderIds.INTAKE_CALORIES,
                todayKey = todayKey,
                title = title,
                message = context.getString(R.string.goal_exceeded_calorie_message),
            )
        }

        if (macros.proteinGrams > 0 && totalProtein > macros.proteinGrams) {
            notifyOnce(
                metricKey = METRIC_PROTEIN,
                notificationId = ReminderIds.INTAKE_PROTEIN,
                todayKey = todayKey,
                title = title,
                message = context.getString(
                    R.string.goal_exceeded_macro_message,
                    context.getString(R.string.protein),
                ),
            )
        }

        if (macros.carbsGrams > 0 && totalCarbs > macros.carbsGrams) {
            notifyOnce(
                metricKey = METRIC_CARBS,
                notificationId = ReminderIds.INTAKE_CARBS,
                todayKey = todayKey,
                title = title,
                message = context.getString(
                    R.string.goal_exceeded_macro_message,
                    context.getString(R.string.carb),
                ),
            )
        }

        if (macros.fatGrams > 0 && totalFat > macros.fatGrams) {
            notifyOnce(
                metricKey = METRIC_FAT,
                notificationId = ReminderIds.INTAKE_FAT,
                todayKey = todayKey,
                title = title,
                message = context.getString(
                    R.string.goal_exceeded_macro_message,
                    context.getString(R.string.fat),
                ),
            )
        }
    }

    fun cancelIntakeNotifications() {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(ReminderIds.INTAKE_CALORIES)
        manager.cancel(ReminderIds.INTAKE_PROTEIN)
        manager.cancel(ReminderIds.INTAKE_CARBS)
        manager.cancel(ReminderIds.INTAKE_FAT)
    }

    @SuppressLint("MissingPermission")
    private fun notifyOnce(
        metricKey: String,
        notificationId: Int,
        todayKey: String,
        title: String,
        message: String,
    ) {
        if (notificationPreferences.wasWarnedToday(metricKey, todayKey)) return
        if (!canPostNotifications()) {
            Log.w(TAG, "Skipping intake warning for $metricKey — notifications not allowed")
            return
        }

        runCatching {
            reminderScheduler.postNotification(
                notificationId = notificationId,
                title = title,
                message = message,
                channelId = ReminderScheduler.INTAKE_CHANNEL_ID,
            )
            notificationPreferences.markWarnedToday(metricKey, todayKey)
            Log.d(TAG, "Posted intake warning for $metricKey")
        }.onFailure { error ->
            Log.w(TAG, "Failed to show intake warning for $metricKey", error)
        }
    }

    private fun canPostNotifications(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications disabled at system level")
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted")
                return false
            }
        }
        return true
    }

    private companion object {
        const val TAG = "IntakeThresholdMonitor"
    }
}
