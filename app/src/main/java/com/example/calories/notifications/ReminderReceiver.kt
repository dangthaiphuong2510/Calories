package com.example.calories.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calories.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REMINDER) return

        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE)
            ?: context.getString(R.string.notification_settings_title)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
            ?: context.getString(R.string.reminder_generic_message)
        val hour = intent.getIntExtra(EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)

        ReminderScheduler(context.applicationContext).postNotification(
            notificationId = alarmId,
            title = title,
            message = message,
        )

        ReminderScheduler(context.applicationContext).scheduleDailyAlarm(
            id = alarmId,
            hour = hour,
            minute = minute,
            title = title,
            message = message,
        )
    }

    companion object {
        const val ACTION_REMINDER = "com.example.calories.action.REMINDER"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
    }
}
