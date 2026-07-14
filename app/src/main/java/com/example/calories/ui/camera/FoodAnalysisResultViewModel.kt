package com.example.calories.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodAnalysisResultUiState(
    val isSaving: Boolean = false,
)

sealed interface FoodAnalysisResultNavEvent {
    data object Saved : FoodAnalysisResultNavEvent
}

@HiltViewModel
class FoodAnalysisResultViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FoodAnalysisResultUiState())
    val uiState: StateFlow<FoodAnalysisResultUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<FoodAnalysisResultNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun saveFood(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        mealType: MealType,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                foodRepository.addFoodEntry(name, calories, protein, carb, fat, mealType)
                _navEvents.send(FoodAnalysisResultNavEvent.Saved)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(UiEvent.Message(e.message ?: "Could not add food"))
            }
        }
    }
}
