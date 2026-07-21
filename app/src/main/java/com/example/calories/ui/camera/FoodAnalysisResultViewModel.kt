package com.example.calories.ui.camera

import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.network.service.GeminiAnalysisService
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.mapGeminiErrorToUiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt

data class OriginalFoodData(
    val calories: Int,
    val proteinGrams: Float,
    val carbsGrams: Float,
    val fatGrams: Float,
    val portionWeightGrams: Int,
)

data class FoodAnalysisResultUiState(
    val isAnalyzing: Boolean = true,
    val isSaving: Boolean = false,
    val isFoodDetected: Boolean = true,
    val analysis: FoodAnalysisResult? = null,
    val originalData: OriginalFoodData? = null,
    val foodName: String = "",
    val caloriesText: String = "",
    val weightText: String = "",
    val proteinText: String = "",
    val carbsText: String = "",
    val fatText: String = "",
    val mealType: MealType = defaultMealTypeForNow(),
    val ingredients: List<String> = emptyList(),
) {
    val calories: Int get() = caloriesText.toIntOrNull() ?: 0
    val weightGrams: Int get() = weightText.toIntOrNull() ?: 0
    val proteinGrams: Float get() = proteinText.toFloatOrNull() ?: 0f
    val carbsGrams: Float get() = carbsText.toFloatOrNull() ?: 0f
    val fatGrams: Float get() = fatText.toFloatOrNull() ?: 0f
}

sealed interface FoodAnalysisResultNavEvent {
    data class Saved(val mealType: MealType) : FoodAnalysisResultNavEvent
}

@HiltViewModel
class FoodAnalysisResultViewModel @Inject constructor(
    private val geminiAnalysisService: GeminiAnalysisService,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodAnalysisResultUiState())
    val uiState: StateFlow<FoodAnalysisResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<FoodAnalysisResultNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun analyzeImage(imagePath: String) {
        if (_uiState.value.analysis != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true) }
            try {
                val bitmap = BitmapFactory.decodeFile(File(imagePath).absolutePath)
                    ?: throw IllegalArgumentException("Could not decode selected image")
                val result = geminiAnalysisService.analyzeFoodImage(bitmap)
                applyAnalysisResult(result)
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false) }
                _events.send(mapGeminiErrorToUiEvent(e))
            }
        }
    }

    fun applyPreAnalyzedResult(result: FoodAnalysisResult) {
        if (_uiState.value.analysis != null) return
        applyAnalysisResult(result)
    }

    private fun applyAnalysisResult(result: FoodAnalysisResult) {
        if (!result.isFoodDetected) {
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    isFoodDetected = false,
                    analysis = result,
                )
            }
            return
        }

        val original = OriginalFoodData(
            calories = result.calories,
            proteinGrams = result.proteinGrams,
            carbsGrams = result.carbsGrams,
            fatGrams = result.fatGrams,
            portionWeightGrams = result.estimatedWeightGrams.coerceAtLeast(1),
        )
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                isFoodDetected = true,
                analysis = result,
                originalData = original,
                foodName = result.foodName,
                caloriesText = result.calories.toString(),
                weightText = result.estimatedWeightGrams.toString(),
                proteinText = formatMacro(result.proteinGrams),
                carbsText = formatMacro(result.carbsGrams),
                fatText = formatMacro(result.fatGrams),
                mealType = defaultMealTypeForNow(),
                ingredients = result.ingredients,
            )
        }
    }

    fun onFoodNameChanged(value: String) {
        _uiState.update { it.copy(foodName = value) }
    }

    fun onWeightChanged(value: String) {
        _uiState.update { state ->
            val original = state.originalData
                ?: return@update state.copy(weightText = value)

            val newWeight = value.toIntOrNull()
            if (newWeight == null || newWeight <= 0) {
                return@update state.copy(weightText = value)
            }

            val ratio = newWeight.toFloat() / original.portionWeightGrams
            state.copy(
                weightText = value,
                caloriesText = (original.calories * ratio).roundToInt().toString(),
                proteinText = formatMacro(original.proteinGrams * ratio),
                carbsText = formatMacro(original.carbsGrams * ratio),
                fatText = formatMacro(original.fatGrams * ratio),
            )
        }
    }

    fun onMealTypeChanged(mealType: MealType) {
        _uiState.update { it.copy(mealType = mealType) }
    }

    fun saveFood() {
        val state = _uiState.value
        val name = state.foodName.trim()
        val calories = state.caloriesText.toIntOrNull()
        val weight = state.weightText.toIntOrNull()
        if (name.isBlank() || calories == null || weight == null || weight <= 0) {
            viewModelScope.launch {
                _events.send(UiEvent.MessageRes(R.string.error_fill_all_fields))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Same day-bucketing as Search/Recipe flows so Home's isSameDay filter matches.
                foodRepository.addFoodEntry(
                    name = name,
                    calories = calories,
                    protein = state.proteinGrams.toDouble(),
                    carb = state.carbsGrams.toDouble(),
                    fat = state.fatGrams.toDouble(),
                    mealType = state.mealType,
                    servingGrams = weight.toDouble(),
                    recordedAt = DateTimeUtils.atNowOnDateIso(DateTimeUtils.today()),
                )
                _navEvents.send(FoodAnalysisResultNavEvent.Saved(state.mealType))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(UiEvent.Message(e.message ?: "Could not add food"))
            }
        }
    }

    private fun formatMacro(value: Float): String {
        return if (value % 1f == 0f) value.toInt().toString() else "%.1f".format(value)
    }
}

internal fun defaultMealTypeForNow(): MealType {
    val hour = LocalTime.now(ZoneId.systemDefault()).hour
    return when (hour) {
        in 5..10 -> MealType.BREAKFAST
        in 11..14 -> MealType.LUNCH
        in 15..20 -> MealType.DINNER
        else -> MealType.SNACKS
    }
}
