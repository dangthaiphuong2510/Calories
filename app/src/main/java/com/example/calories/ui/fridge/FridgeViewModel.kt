package com.example.calories.ui.fridge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FridgeRepository
import com.example.calories.model.FridgeIngredient
import com.example.calories.ui.common.UiEvent
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

data class FridgeUiState(
    val ingredients: List<FridgeIngredient> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FridgeViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val fridgeRepository: FridgeRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState: StateFlow<FridgeUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(FridgeUiState())
            } else {
                fridgeRepository.observeIngredients(id).map { FridgeUiState(it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FridgeUiState())

    init {
        refresh()
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { fridgeRepository.refresh(id) }
        }
    }

    fun addIngredient(name: String, quantity: Double, unit: String, expiryDate: String?) {
        viewModelScope.launch {
            try {
                fridgeRepository.addIngredient(name, quantity, unit, expiryDate)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add ingredient"))
            }
        }
    }
}
