package com.example.calories.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.calories.data.preferences.NotificationPreferences
import com.example.calories.widget.WidgetRefreshBridge

/**
 * Restores exact alarms after reboot or app update (AlarmManager does not persist them).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) {
            return
        }

        val appContext = context.applicationContext
        val settings = NotificationPreferences(appContext).load()
        ReminderScheduler(appContext).syncAll(settings)
        WidgetRefreshBridge.refresh(appContext)
    }
}
