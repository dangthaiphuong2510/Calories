package com.example.calories.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.example.calories.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CaloriesHomeWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetUpdater: CaloriesWidgetUpdater

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        widgetUpdater.scheduleRefresh()
    }

    override fun onEnabled(context: Context) {
        widgetUpdater.scheduleRefresh()
    }

    companion object {
        const val EXTRA_OPEN_PROGRESS = "extra_open_progress"

        fun createOpenAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_PROGRESS, true)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
