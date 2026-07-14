package com.example.calories.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FoodLogUiState(
    val dateLabel: String = DateTimeUtils.todayDisplay(),
    val entries: List<FoodEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodLogViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<FoodLogUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(FoodLogUiState())
            } else {
                foodRepository.observeFoodEntries(id).map { entries ->
                    FoodLogUiState(
                        dateLabel = DateTimeUtils.todayDisplay(),
                        entries = entries.filter { DateTimeUtils.isToday(it.createdAt) },
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodLogUiState())

    init {
        refresh()
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { foodRepository.refresh(id) }
        }
    }

    fun addFood(
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        mealType: MealType,
    ) {
        viewModelScope.launch {
            try {
                foodRepository.addFoodEntry(name, calories, protein, carb, fat, mealType)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add food"))
            }
        }
    }
}
