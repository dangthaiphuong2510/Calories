package com.example.calories.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.repository.ExerciseRepository
import com.example.calories.model.ExercisePreset
import com.example.calories.model.StandardExercises
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

enum class ExerciseLoggerMode {
    PRESET,
    CUSTOM,
}

data class ExerciseLoggerUiState(
    val mode: ExerciseLoggerMode = ExerciseLoggerMode.PRESET,
    val query: String = "",
    val presets: List<ExercisePreset> = emptyList(),
    val customName: String = "",
    val customCalories: String = "",
    val customDuration: String = "",
    val saveToCustomDb: Boolean = false,
    val addToTodayLog: Boolean = true,
    val selectedDate: LocalDate = DateTimeUtils.today(),
    val isSaving: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ExerciseLoggerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {

    private val _mode = MutableStateFlow(ExerciseLoggerMode.PRESET)
    private val _query = MutableStateFlow("")
    private val _customName = MutableStateFlow("")
    private val _customCalories = MutableStateFlow("")
    private val _customDuration = MutableStateFlow("")
    private val _saveToCustomDb = MutableStateFlow(false)
    private val _addToTodayLog = MutableStateFlow(true)
    private val _selectedDate = MutableStateFlow(DateTimeUtils.today())
    private val _isSaving = MutableStateFlow(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _logged = Channel<Unit>(Channel.BUFFERED)
    val logged = _logged.receiveAsFlow()

    val uiState: StateFlow<ExerciseLoggerUiState> = combine(
        combine(_mode, _query.debounce(200), exerciseRepository.observeCustomExercises()) { mode, query, custom ->
            Triple(mode, query, custom)
        },
        combine(_customName, _customCalories, _customDuration) { name, cals, duration ->
            Triple(name, cals, duration)
        },
        combine(_saveToCustomDb, _addToTodayLog, _selectedDate, _isSaving) { saveCustom, addToday, date, saving ->
            Quad(saveCustom, addToday, date, saving)
        },
    ) { modeQueryCustom, customFields, flags ->
        val (mode, query, custom) = modeQueryCustom
        val (name, cals, duration) = customFields
        val (saveCustom, addToday, date, saving) = flags
        val combined = (StandardExercises.presets + custom)
            .filter { preset ->
                query.isBlank() ||
                    preset.name.lowercase(Locale.getDefault())
                        .contains(query.lowercase(Locale.getDefault()))
            }
        ExerciseLoggerUiState(
            mode = mode,
            query = query,
            presets = combined,
            customName = name,
            customCalories = cals,
            customDuration = duration,
            saveToCustomDb = saveCustom,
            addToTodayLog = addToday,
            selectedDate = date,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLoggerUiState())

    fun initialize(selectedDate: LocalDate) {
        _selectedDate.value = selectedDate
    }

    fun setMode(mode: ExerciseLoggerMode) {
        _mode.value = mode
    }

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun onCustomNameChanged(value: String) {
        _customName.value = value
    }

    fun onCustomCaloriesChanged(value: String) {
        _customCalories.value = value
    }

    fun onCustomDurationChanged(value: String) {
        _customDuration.value = value
    }

    fun onSaveToCustomDbChanged(checked: Boolean) {
        _saveToCustomDb.value = checked
    }

    fun onAddToTodayLogChanged(checked: Boolean) {
        _addToTodayLog.value = checked
    }

    fun logPreset(preset: ExercisePreset) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                exerciseRepository.addExerciseEntry(
                    name = preset.name,
                    caloriesBurned = preset.calories.toDouble(),
                    durationMinutes = preset.durationMinutes,
                    createdAt = DateTimeUtils.atNoonIso(_selectedDate.value),
                )
                _events.send(UiEvent.MessageRes(R.string.exercise_added_toast))
                _logged.send(Unit)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not log exercise"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun submitCustom() {
        val name = _customName.value.trim()
        val calories = _customCalories.value.toIntOrNull() ?: 0
        val duration = _customDuration.value.toIntOrNull() ?: 0
        if (name.isBlank() || calories <= 0 || duration <= 0) {
            viewModelScope.launch {
                _events.send(UiEvent.MessageRes(R.string.error_fill_all_fields))
            }
            return
        }
        if (!_saveToCustomDb.value && !_addToTodayLog.value) {
            viewModelScope.launch {
                _events.send(UiEvent.MessageRes(R.string.exercise_select_action))
            }
            return
        }

        viewModelScope.launch {
            _isSaving.value = true
            try {
                if (_saveToCustomDb.value) {
                    exerciseRepository.addCustomExercise(name, calories, duration)
                }
                if (_addToTodayLog.value) {
                    exerciseRepository.addExerciseEntry(
                        name = name,
                        caloriesBurned = calories.toDouble(),
                        durationMinutes = duration,
                        createdAt = DateTimeUtils.atNoonIso(_selectedDate.value),
                    )
                }
                _events.send(UiEvent.MessageRes(R.string.exercise_added_toast))
                if (_addToTodayLog.value) {
                    _logged.send(Unit)
                }
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not save exercise"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
