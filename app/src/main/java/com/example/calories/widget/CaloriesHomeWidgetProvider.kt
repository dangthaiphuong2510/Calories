package com.example.calories.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.calories.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class CaloriesHomeWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var widgetUpdater: CaloriesWidgetUpdater
    @Inject lateinit var waterActionHandler: WidgetWaterActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_ADD_WATER) {
            val pendingResult = goAsync()

            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    withTimeout(5000) {
                        val added = waterActionHandler.addWaterFromWidget()
                        if (added) {
                            widgetUpdater.updateAll()
                            Log.d(TAG, "Widget +250ml added successfully")
                        }
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    Log.e(TAG, "Widget action timed out, finishing broadcast to prevent ANR", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget +250ml tap failed", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        widgetUpdater.scheduleRefresh()
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        widgetUpdater.scheduleRefresh()
    }

    companion object {
        const val ACTION_ADD_WATER = "com.example.calories.widget.ACTION_ADD_WATER"
        const val EXTRA_OPEN_HOME = "extra_open_home"
        private const val TAG = "CaloriesWidget"
        private const val REQUEST_ADD_WATER = 1

        fun createOpenAppPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_HOME, true)
            }
            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun createAddWaterPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, CaloriesHomeWidgetProvider::class.java).apply {
                action = ACTION_ADD_WATER
            }
            return PendingIntent.getBroadcast(
                context,
                REQUEST_ADD_WATER,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}