package com.example.calories

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CaloriesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

    }
}