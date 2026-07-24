package com.example.calories.widget

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetRefreshNotifier @Inject constructor(
    private val widgetUpdater: CaloriesWidgetUpdater,
) : WidgetRefresher {
    override fun notifyDataChanged() {
        widgetUpdater.scheduleRefresh()
    }
}
