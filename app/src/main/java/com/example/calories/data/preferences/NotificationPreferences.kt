package com.example.calories.data.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationSettings(
    val mealRemindersEnabled: Boolean = true,
    val breakfastTime: String = "06:00",
    val lunchTime: String = "11:20",
    val dinnerTime: String = "19:00",
    val snacksTime: String = "21:00",
    val waterRemindersEnabled: Boolean = true,
    val waterTimes: List<String> = listOf("08:00", "12:00", "16:00", "20:00"),
    val workoutRemindersEnabled: Boolean = false,
    val workoutTimes: List<String> = listOf("07:00"),
)

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): NotificationSettings = NotificationSettings(
        mealRemindersEnabled = prefs.getBoolean(KEY_MEAL_ENABLED, true),
        breakfastTime = prefs.getString(KEY_BREAKFAST, "06:00") ?: "06:00",
        lunchTime = prefs.getString(KEY_LUNCH, "11:20") ?: "11:20",
        dinnerTime = prefs.getString(KEY_DINNER, "19:00") ?: "19:00",
        snacksTime = prefs.getString(KEY_SNACKS, "21:00") ?: "21:00",
        waterRemindersEnabled = prefs.getBoolean(KEY_WATER_ENABLED, true),
        waterTimes = readTimes(KEY_WATER_TIMES, DEFAULT_WATER_TIMES),
        workoutRemindersEnabled = prefs.getBoolean(KEY_WORKOUT_ENABLED, false),
        workoutTimes = readTimes(KEY_WORKOUT_TIMES, DEFAULT_WORKOUT_TIMES),
    )

    fun save(settings: NotificationSettings) {
        prefs.edit()
            .putBoolean(KEY_MEAL_ENABLED, settings.mealRemindersEnabled)
            .putString(KEY_BREAKFAST, settings.breakfastTime)
            .putString(KEY_LUNCH, settings.lunchTime)
            .putString(KEY_DINNER, settings.dinnerTime)
            .putString(KEY_SNACKS, settings.snacksTime)
            .putBoolean(KEY_WATER_ENABLED, settings.waterRemindersEnabled)
            .putString(KEY_WATER_TIMES, toJson(settings.waterTimes))
            .putBoolean(KEY_WORKOUT_ENABLED, settings.workoutRemindersEnabled)
            .putString(KEY_WORKOUT_TIMES, toJson(settings.workoutTimes))
            .apply()
    }

    private fun readTimes(key: String, defaults: List<String>): List<String> {
        val raw = prefs.getString(key, null) ?: return defaults
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }.ifEmpty { defaults }
        }.getOrDefault(defaults)
    }

    private fun toJson(times: List<String>): String {
        val array = JSONArray()
        times.forEach { array.put(it) }
        return array.toString()
    }

    companion object {
        const val PREFS_NAME = "notification_settings"
        private const val KEY_MEAL_ENABLED = "meal_enabled"
        private const val KEY_BREAKFAST = "breakfast_time"
        private const val KEY_LUNCH = "lunch_time"
        private const val KEY_DINNER = "dinner_time"
        private const val KEY_SNACKS = "snacks_time"
        private const val KEY_WATER_ENABLED = "water_enabled"
        private const val KEY_WATER_TIMES = "water_times"
        private const val KEY_WORKOUT_ENABLED = "workout_enabled"
        private const val KEY_WORKOUT_TIMES = "workout_times"

        private val DEFAULT_WATER_TIMES = listOf("08:00", "12:00", "16:00", "20:00")
        private val DEFAULT_WORKOUT_TIMES = listOf("07:00")
    }
}
