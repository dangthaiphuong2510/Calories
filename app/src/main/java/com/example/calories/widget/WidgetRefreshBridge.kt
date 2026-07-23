package com.example.calories.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRefreshEntryPoint {
    fun widgetRefreshNotifier(): WidgetRefreshNotifier
}

object WidgetRefreshBridge {
    fun refresh(context: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetRefreshEntryPoint::class.java,
        )
        entryPoint.widgetRefreshNotifier().notifyDataChanged()
    }
}
