package com.example.calories.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.calories.R
import com.example.calories.ui.MainActivity

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

        showNotification(context, alarmId, title, message)

        // setExact* is one-shot — reschedule for the next day
        ReminderScheduler(context.applicationContext).scheduleDailyAlarm(
            id = alarmId,
            hour = hour,
            minute = minute,
            title = title,
            message = message,
        )
    }

    private fun showNotification(
        context: Context,
        alarmId: Int,
        title: String,
        message: String,
    ) {
        ReminderScheduler(context.applicationContext).ensureChannel()

        val contentIntent = PendingIntent.getActivity(
            context,
            alarmId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(alarmId, notification)
        }
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
