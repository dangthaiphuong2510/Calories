package com.example.calories.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.ExploreKcalFilter
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodSearchFilter
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExploreUiState(
    val query: String = "",
    val selectedFilter: ExploreKcalFilter = ExploreKcalFilter.ALL,
    val results: List<FoodDictionaryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(ExploreKcalFilter.ALL)

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        observeSearchInputs()
    }

    fun onQueryChanged(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
    }

    fun onFilterSelected(filter: ExploreKcalFilter) {
        _selectedFilter.value = filter
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    private fun observeSearchInputs() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            combine(
                _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
                _selectedFilter,
            ) { query, filter ->
                query to filter
            }.collectLatest { (query, filter) ->
                _uiState.update { it.copy(isLoading = true) }
                val results = runCatching {
                    loadResults(query = query, filter = filter)
                }.getOrElse { emptyList() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        isEmpty = results.isEmpty(),
                        query = query,
                        selectedFilter = filter,
                    )
                }
            }
        }
    }

    private suspend fun loadResults(
        query: String,
        filter: ExploreKcalFilter,
    ): List<FoodDictionaryItem> {
        val dictionary = foodRepository.searchFoodDictionary(
            query = query,
            filter = FoodSearchFilter.ALL,
            limit = SEARCH_LIMIT,
        )
        return applyKcalFilter(dictionary, filter)
    }

    private fun applyKcalFilter(
        items: List<FoodDictionaryItem>,
        filter: ExploreKcalFilter,
    ): List<FoodDictionaryItem> {
        return when (filter) {
            ExploreKcalFilter.ALL -> items
            ExploreKcalFilter.UNDER_200 -> items.filter { it.caloriesInt < 200 }
            ExploreKcalFilter.FROM_200_TO_400 -> items.filter { it.caloriesInt in 200..400 }
            ExploreKcalFilter.FROM_400_TO_600 -> items.filter { it.caloriesInt in 401..600 }
            ExploreKcalFilter.OVER_600 -> items.filter { it.caloriesInt > 600 }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val SEARCH_LIMIT = 80
    }
}
