package com.example.calories.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.UserGoalsRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val dateLabel: String = DateTimeUtils.todayDisplay(),
    val caloriesEaten: Int = 0,
    val calorieGoal: Int = 2000,
    val caloriesRemaining: Int = 2000,
    val progressPercent: Int = 0,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val recentMeals: List<FoodEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val foodRepository: FoodRepository,
    private val userGoalsRepository: UserGoalsRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(HomeUiState())
            } else {
                val (startOfDay, startOfTomorrow) = DateTimeUtils.todayRange()
                combine(
                    foodRepository.observeFoodEntries(id),
                    foodRepository.observeTodayCalories(id, startOfDay, startOfTomorrow),
                    userGoalsRepository.observeGoal(id),
                ) { entries, eaten, goal ->
                    val todayEntries = entries.filter { DateTimeUtils.isToday(it.createdAt) }
                    val goalCalories = goal?.dailyCalories ?: 2000
                    val remaining = (goalCalories - eaten).coerceAtLeast(0)
                    val progress = if (goalCalories == 0) {
                        0
                    } else {
                        ((eaten.toFloat() / goalCalories) * 100).toInt().coerceIn(0, 100)
                    }
                    HomeUiState(
                        dateLabel = DateTimeUtils.todayDisplay(),
                        caloriesEaten = eaten,
                        calorieGoal = goalCalories,
                        caloriesRemaining = remaining,
                        progressPercent = progress,
                        protein = todayEntries.sumOf { it.protein },
                        carb = todayEntries.sumOf { it.carb },
                        fat = todayEntries.sumOf { it.fat },
                        recentMeals = todayEntries.take(5),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh()
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { foodRepository.refresh(id) }
            runCatching { userGoalsRepository.refresh(id) }
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
