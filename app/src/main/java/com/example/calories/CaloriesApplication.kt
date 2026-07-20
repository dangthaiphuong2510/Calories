package com.example.calories

import android.app.Application
import com.example.calories.data.preferences.AppPreferences
import com.example.calories.notifications.ReminderScheduler
import com.google.android.gms.ads.MobileAds
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class CaloriesApplication : Application() {

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this)
        reminderScheduler.ensureChannel()
        appPreferences.applyStoredSettings()
    }
}
