package com.example.calories.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.calories.insights.InsightWeekKeys
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedIds = MutableStateFlow(readDismissedForCurrentWeek())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()

    fun dismiss(insightId: String) {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        val current = if (storedWeek == week) {
            prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }
        current += insightId
        prefs.edit()
            .putString(KEY_WEEK, week)
            .putStringSet(KEY_IDS, current)
            .apply()
        _dismissedIds.value = current.toSet()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _dismissedIds.value = emptySet()
    }

    fun ensureCurrentWeek() {
        val current = readDismissedForCurrentWeek()
        if (_dismissedIds.value != current) {
            _dismissedIds.value = current
        }
    }

    private fun readDismissedForCurrentWeek(): Set<String> {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        if (storedWeek != week) {
            prefs.edit().clear().apply()
            return emptySet()
        }
        return prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
    }

    companion object {
        const val PREFS_NAME = "insight_prefs"
        private const val KEY_WEEK = "week_key"
        private const val KEY_IDS = "dismissed_ids"
    }
}
