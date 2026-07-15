package com.example.calories.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class FoodDetailUiState(
    val name: String = "",
    val mealType: MealType = MealType.SNACK,
    val selectedDate: LocalDate = DateTimeUtils.today(),
    val portionGrams: Double = 100.0,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val burnMinutesWalk: Int = 0,
    val burnMinutesRun: Int = 0,
    val burnMinutesCycle: Int = 0,
    val viewOnly: Boolean = false,
    val isSaving: Boolean = false,
) {
    val totalMacros: Double get() = (protein + carb + fat).coerceAtLeast(0.01)
    val proteinPercent: Float get() = (protein / totalMacros).toFloat()
    val carbPercent: Float get() = (carb / totalMacros).toFloat()
    val fatPercent: Float get() = (fat / totalMacros).toFloat()
}

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val _baseCalories = MutableStateFlow(0)
    private val _baseProtein = MutableStateFlow(0.0)
    private val _baseCarb = MutableStateFlow(0.0)
    private val _baseFat = MutableStateFlow(0.0)
    private val _baseGrams = MutableStateFlow(BASE_PORTION_GRAMS)
    private val _portionGrams = MutableStateFlow(BASE_PORTION_GRAMS)
    private val _name = MutableStateFlow("")
    private val _mealType = MutableStateFlow(MealType.SNACK)
    private val _selectedDate = MutableStateFlow(DateTimeUtils.today())
    private val _viewOnly = MutableStateFlow(false)
    private val _isSaving = MutableStateFlow(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _logged = Channel<MealType>(Channel.BUFFERED)
    val logged = _logged.receiveAsFlow()

    val uiState: StateFlow<FoodDetailUiState> = combine(
        combine(_name, _mealType, _selectedDate, _portionGrams) { name, meal, date, portion ->
            Quad(name, meal, date, portion)
        },
        combine(
            combine(_baseCalories, _baseProtein) { cals, p -> cals to p },
            combine(_baseCarb, _baseFat, _baseGrams) { c, f, base -> Triple(c, f, base) },
        ) { calsProtein, carbFatBase ->
            Nutrients(
                calories = calsProtein.first,
                protein = calsProtein.second,
                carb = carbFatBase.first,
                fat = carbFatBase.second,
                baseGrams = carbFatBase.third,
            )
        },
        combine(_viewOnly, _isSaving) { viewOnly, saving -> viewOnly to saving },
    ) { identity, nutrients, flags ->
        val (name, meal, date, portion) = identity
        val (viewOnly, saving) = flags
        val scale = if (nutrients.baseGrams <= 0.0) 0.0 else portion / nutrients.baseGrams
        val calories = (nutrients.calories * scale).roundToInt()
        val protein = nutrients.protein * scale
        val carb = nutrients.carb * scale
        val fat = nutrients.fat * scale
        FoodDetailUiState(
            name = name,
            mealType = meal,
            selectedDate = date,
            portionGrams = portion,
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            burnMinutesWalk = burnMinutes(calories, WALK_KCAL_PER_MIN),
            burnMinutesRun = burnMinutes(calories, RUN_KCAL_PER_MIN),
            burnMinutesCycle = burnMinutes(calories, CYCLE_KCAL_PER_MIN),
            viewOnly = viewOnly,
            isSaving = saving,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDetailUiState())

    fun initialize(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        servingGrams: Double,
        mealType: MealType,
        selectedDate: LocalDate,
        viewOnly: Boolean,
    ) {
        _name.value = name
        _baseCalories.value = calories
        _baseProtein.value = protein
        _baseCarb.value = carb
        _baseFat.value = fat
        val grams = servingGrams.takeIf { it > 0.0 } ?: BASE_PORTION_GRAMS
        _baseGrams.value = grams
        _portionGrams.value = if (viewOnly) grams else BASE_PORTION_GRAMS
        _mealType.value = mealType
        _selectedDate.value = selectedDate
        _viewOnly.value = viewOnly
    }

    fun onPortionChanged(raw: String) {
        val value = raw.toDoubleOrNull() ?: return
        if (value <= 0.0) return
        _portionGrams.value = value
    }

    fun logFood() {
        if (_viewOnly.value || _isSaving.value) return
        val state = uiState.value
        viewModelScope.launch {
            _isSaving.value = true
            try {
                foodRepository.addFoodEntry(
                    name = state.name,
                    calories = state.calories,
                    protein = state.protein,
                    carb = state.carb,
                    fat = state.fat,
                    mealType = state.mealType,
                    servingGrams = state.portionGrams,
                    recordedAt = DateTimeUtils.atNoonIso(state.selectedDate),
                )
                _events.send(UiEvent.MessageRes(R.string.food_added_toast))
                _logged.send(state.mealType)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add food"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun burnMinutes(calories: Int, kcalPerMin: Double): Int {
        if (calories <= 0 || kcalPerMin <= 0.0) return 0
        return (calories / kcalPerMin).roundToInt().coerceAtLeast(1)
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private data class Nutrients(
        val calories: Int,
        val protein: Double,
        val carb: Double,
        val fat: Double,
        val baseGrams: Double,
    )

    private companion object {
        const val BASE_PORTION_GRAMS = 100.0
        const val WALK_KCAL_PER_MIN = 4.0
        const val RUN_KCAL_PER_MIN = 10.0
        const val CYCLE_KCAL_PER_MIN = 8.0
    }
}
