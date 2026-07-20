package com.example.calories.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.preferences.NotificationPreferences
import com.example.calories.data.preferences.NotificationSettings
import com.example.calories.model.enums.MealType
import com.example.calories.notifications.IntakeThresholdMonitor
import com.example.calories.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class NotificationSettingsUiState(
    val mealRemindersEnabled: Boolean = true,
    val breakfastTime: String = "06:00",
    val lunchTime: String = "11:20",
    val dinnerTime: String = "19:00",
    val snacksTime: String = "21:00",
    val waterRemindersEnabled: Boolean = true,
    val waterTimes: List<String> = emptyList(),
    val workoutRemindersEnabled: Boolean = false,
    val workoutTimes: List<String> = emptyList(),
    val intakeWarningsEnabled: Boolean = true,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val preferences: NotificationPreferences,
    private val reminderScheduler: ReminderScheduler,
    private val intakeThresholdMonitor: IntakeThresholdMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    init {
        val loaded = preferences.load().toUiState()
        _uiState.value = loaded
        viewModelScope.launch {
            syncAlarms(loaded.toSettings())
        }
    }

    fun setMealRemindersEnabled(enabled: Boolean) {
        updateAndPersist(
            transform = { it.copy(mealRemindersEnabled = enabled) },
            sync = { reminderScheduler.syncMealAlarms(it) },
        )
    }

    fun setWaterRemindersEnabled(enabled: Boolean) {
        updateAndPersist(
            transform = { it.copy(waterRemindersEnabled = enabled) },
            sync = { reminderScheduler.syncWaterAlarms(it) },
        )
    }

    fun setWorkoutRemindersEnabled(enabled: Boolean) {
        updateAndPersist(
            transform = { it.copy(workoutRemindersEnabled = enabled) },
            sync = { reminderScheduler.syncWorkoutAlarms(it) },
        )
    }

    fun setIntakeWarningsEnabled(enabled: Boolean) {
        if (!enabled) {
            preferences.clearIntakeWarningMarks()
            intakeThresholdMonitor.cancelIntakeNotifications()
        }
        updateAndPersist(
            transform = { it.copy(intakeWarningsEnabled = enabled) },
            sync = { },
        )
    }

    fun setMealTime(mealType: MealType, time: String) {
        updateAndPersist(
            transform = { state ->
                when (mealType) {
                    MealType.BREAKFAST -> state.copy(breakfastTime = time)
                    MealType.LUNCH -> state.copy(lunchTime = time)
                    MealType.DINNER -> state.copy(dinnerTime = time)
                    MealType.SNACKS -> state.copy(snacksTime = time)
                }
            },
            sync = { reminderScheduler.syncMealAlarms(it) },
        )
    }

    fun addWaterTime(time: String) {
        updateAndPersist(
            transform = { state ->
                if (time in state.waterTimes) state
                else state.copy(waterTimes = (state.waterTimes + time).sorted())
            },
            sync = { reminderScheduler.syncWaterAlarms(it) },
        )
    }

    fun updateWaterTime(index: Int, time: String) {
        updateAndPersist(
            transform = { state ->
                if (index !in state.waterTimes.indices) return@updateAndPersist state
                val updated = state.waterTimes.toMutableList().also { it[index] = time }.sorted()
                state.copy(waterTimes = updated)
            },
            sync = { reminderScheduler.syncWaterAlarms(it) },
        )
    }

    fun removeWaterTime(index: Int) {
        updateAndPersist(
            transform = { state ->
                if (index !in state.waterTimes.indices) return@updateAndPersist state
                state.copy(waterTimes = state.waterTimes.toMutableList().also { it.removeAt(index) })
            },
            sync = { reminderScheduler.syncWaterAlarms(it) },
        )
    }

    fun addWorkoutTime(time: String) {
        updateAndPersist(
            transform = { state ->
                if (time in state.workoutTimes) state
                else state.copy(workoutTimes = (state.workoutTimes + time).sorted())
            },
            sync = { reminderScheduler.syncWorkoutAlarms(it) },
        )
    }

    fun updateWorkoutTime(index: Int, time: String) {
        updateAndPersist(
            transform = { state ->
                if (index !in state.workoutTimes.indices) return@updateAndPersist state
                val updated = state.workoutTimes.toMutableList().also { it[index] = time }.sorted()
                state.copy(workoutTimes = updated)
            },
            sync = { reminderScheduler.syncWorkoutAlarms(it) },
        )
    }

    fun removeWorkoutTime(index: Int) {
        updateAndPersist(
            transform = { state ->
                if (index !in state.workoutTimes.indices) return@updateAndPersist state
                state.copy(workoutTimes = state.workoutTimes.toMutableList().also { it.removeAt(index) })
            },
            sync = { reminderScheduler.syncWorkoutAlarms(it) },
        )
    }

    private fun updateAndPersist(
        transform: (NotificationSettingsUiState) -> NotificationSettingsUiState,
        sync: (NotificationSettings) -> Unit,
    ) {
        _uiState.update(transform)
        val settings = _uiState.value.toSettings()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                preferences.save(settings)
                sync(settings)
            }
        }
    }

    private suspend fun syncAlarms(settings: NotificationSettings) {
        withContext(Dispatchers.IO) {
            reminderScheduler.syncAll(settings)
        }
    }

    private fun NotificationSettings.toUiState() = NotificationSettingsUiState(
        mealRemindersEnabled = mealRemindersEnabled,
        breakfastTime = breakfastTime,
        lunchTime = lunchTime,
        dinnerTime = dinnerTime,
        snacksTime = snacksTime,
        waterRemindersEnabled = waterRemindersEnabled,
        waterTimes = waterTimes,
        workoutRemindersEnabled = workoutRemindersEnabled,
        workoutTimes = workoutTimes,
        intakeWarningsEnabled = intakeWarningsEnabled,
    )

    private fun NotificationSettingsUiState.toSettings() = NotificationSettings(
        mealRemindersEnabled = mealRemindersEnabled,
        breakfastTime = breakfastTime,
        lunchTime = lunchTime,
        dinnerTime = dinnerTime,
        snacksTime = snacksTime,
        waterRemindersEnabled = waterRemindersEnabled,
        waterTimes = waterTimes,
        workoutRemindersEnabled = workoutRemindersEnabled,
        workoutTimes = workoutTimes,
        intakeWarningsEnabled = intakeWarningsEnabled,
    )
}
