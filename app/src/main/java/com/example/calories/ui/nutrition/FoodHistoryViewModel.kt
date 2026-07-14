package com.example.calories.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.ui.nutrition.adapter.FoodHistoryDay
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class FoodHistoryUiState(
    val days: List<FoodHistoryDay> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoodHistoryViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id
    private val dayFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

    val uiState: StateFlow<FoodHistoryUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(FoodHistoryUiState())
            } else {
                foodRepository.observeFoodEntries(id).map { entries ->
                    val days = entries
                        .groupBy { DateTimeUtils.toLocalDate(it.createdAt) }
                        .filterKeys { it != null }
                        .entries
                        .sortedByDescending { it.key }
                        .map { (date, dayEntries) ->
                            FoodHistoryDay(
                                dateLabel = date!!.format(dayFormatter),
                                entryCount = dayEntries.size,
                                totalCalories = dayEntries.sumOf { it.calories },
                                protein = dayEntries.sumOf { it.protein },
                                carb = dayEntries.sumOf { it.carb },
                                fat = dayEntries.sumOf { it.fat },
                            )
                        }
                    FoodHistoryUiState(days = days)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodHistoryUiState())
}
