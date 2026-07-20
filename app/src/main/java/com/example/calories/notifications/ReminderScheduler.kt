package com.example.calories.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.calories.R
import com.example.calories.ui.MainActivity
import com.example.calories.data.preferences.NotificationSettings
import com.example.calories.model.enums.MealType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun scheduleDailyAlarm(
        id: Int,
        hour: Int,
        minute: Int,
        title: String,
        message: String,
    ) {
        ensureChannel()
        cancelAlarm(id)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(ReminderReceiver.EXTRA_ALARM_ID, id)
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
            putExtra(ReminderReceiver.EXTRA_HOUR, hour)
            putExtra(ReminderReceiver.EXTRA_MINUTE, minute)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val triggerAt = nextTriggerMillis(hour, minute)
        if (canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    fun cancelAlarm(id: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun syncAll(settings: NotificationSettings) {
        ensureChannel()
        syncMealAlarms(settings)
        syncWaterAlarms(settings)
        syncWorkoutAlarms(settings)
    }

    fun syncMealAlarms(settings: NotificationSettings) {
        if (!settings.mealRemindersEnabled) {
            cancelAlarm(ReminderIds.MEAL_BREAKFAST)
            cancelAlarm(ReminderIds.MEAL_LUNCH)
            cancelAlarm(ReminderIds.MEAL_DINNER)
            cancelAlarm(ReminderIds.MEAL_SNACKS)
            return
        }
        scheduleMeal(ReminderIds.MEAL_BREAKFAST, MealType.BREAKFAST, settings.breakfastTime)
        scheduleMeal(ReminderIds.MEAL_LUNCH, MealType.LUNCH, settings.lunchTime)
        scheduleMeal(ReminderIds.MEAL_DINNER, MealType.DINNER, settings.dinnerTime)
        scheduleMeal(ReminderIds.MEAL_SNACKS, MealType.SNACKS, settings.snacksTime)
    }

    fun syncWaterAlarms(settings: NotificationSettings) {
        cancelScheduleRange(ReminderIds.WATER_BASE, ReminderIds.MAX_SCHEDULE_SLOTS)
        if (!settings.waterRemindersEnabled) return
        settings.waterTimes.forEachIndexed { index, time ->
            if (index >= ReminderIds.MAX_SCHEDULE_SLOTS) return@forEachIndexed
            val (hour, minute) = parseTime(time)
            scheduleDailyAlarm(
                id = ReminderIds.water(index),
                hour = hour,
                minute = minute,
                title = context.getString(R.string.reminder_water_title),
                message = context.getString(R.string.reminder_water_message),
            )
        }
    }

    fun syncWorkoutAlarms(settings: NotificationSettings) {
        cancelScheduleRange(ReminderIds.WORKOUT_BASE, ReminderIds.MAX_SCHEDULE_SLOTS)
        if (!settings.workoutRemindersEnabled) return
        settings.workoutTimes.forEachIndexed { index, time ->
            if (index >= ReminderIds.MAX_SCHEDULE_SLOTS) return@forEachIndexed
            val (hour, minute) = parseTime(time)
            scheduleDailyAlarm(
                id = ReminderIds.workout(index),
                hour = hour,
                minute = minute,
                title = context.getString(R.string.reminder_workout_title),
                message = context.getString(R.string.reminder_workout_message),
            )
        }
    }

    private fun scheduleMeal(id: Int, mealType: MealType, time: String) {
        val (hour, minute) = parseTime(time)
        val mealLabel = context.getString(mealLabelRes(mealType))
        scheduleDailyAlarm(
            id = id,
            hour = hour,
            minute = minute,
            title = context.getString(R.string.reminder_meal_title, mealLabel),
            message = context.getString(R.string.reminder_meal_message, mealLabel),
        )
    }

    private fun cancelScheduleRange(baseId: Int, count: Int) {
        repeat(count) { cancelAlarm(baseId + it) }
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return calendar.timeInMillis
    }

    private fun parseTime(value: String): Pair<Int, Int> {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return hour to minute
    }

    private fun mealLabelRes(mealType: MealType): Int = when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACKS -> R.string.meal_snacks
    }

    fun ensureIntakeWarningChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            INTAKE_CHANNEL_ID,
            context.getString(R.string.intake_warning_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.intake_warning_channel_description)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Shared notification poster used by scheduled reminders (water, meals) and
     * immediate intake-threshold alerts.
     */
    fun postNotification(
        notificationId: Int,
        title: String,
        message: String,
        channelId: String = CHANNEL_ID,
    ) {
        when (channelId) {
            INTAKE_CHANNEL_ID -> ensureIntakeWarningChannel()
            else -> ensureChannel()
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    companion object {
        const val CHANNEL_ID = "calories_reminders"
        const val INTAKE_CHANNEL_ID = "calories_intake_warnings"
    }
}
