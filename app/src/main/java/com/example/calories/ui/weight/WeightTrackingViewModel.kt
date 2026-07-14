package com.example.calories.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.data.repository.WeightRepository
import com.example.calories.model.WeightEntry
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

data class WeightUiState(
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val entries: List<WeightEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeightTrackingViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val weightRepository: WeightRepository,
    private val userGoalsRepository: UserGoalsRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<WeightUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(WeightUiState())
            } else {
                combine(
                    weightRepository.observeWeightEntries(id),
                    userGoalsRepository.observeGoal(id),
                ) { entries, goal ->
                    WeightUiState(
                        currentWeightKg = entries.firstOrNull()?.weightKg ?: goal?.currentWeight,
                        targetWeightKg = goal?.targetWeight,
                        entries = entries,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    init {
        refresh()
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { weightRepository.refresh(id) }
            runCatching { userGoalsRepository.refresh(id) }
        }
    }

    fun addWeight(weightKg: Double) {
        viewModelScope.launch {
            try {
                weightRepository.addWeightEntry(weightKg, DateTimeUtils.nowIso())
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not log weight"))
            }
        }
    }
}
