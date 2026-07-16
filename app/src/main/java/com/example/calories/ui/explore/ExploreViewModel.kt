package com.example.calories.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.RecipeRepository
import com.example.calories.model.ExploreRecipeFilter
import com.example.calories.model.Recipe
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.UiState
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
    val selectedFilter: ExploreRecipeFilter = ExploreRecipeFilter.ALL,
    val recipesState: UiState<List<Recipe>> = UiState.Loading,
) {
    val isEmpty: Boolean
        get() = recipesState is UiState.Success && recipesState.data.isEmpty()
}

@OptIn(FlowPreview::class)
@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val recipeRepository: RecipeRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedFilter = MutableStateFlow(ExploreRecipeFilter.ALL)

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

    fun onFilterSelected(filter: ExploreRecipeFilter) {
        _selectedFilter.value = filter
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun retry() {
        viewModelScope.launch {
            loadRecipes(_query.value, _selectedFilter.value)
        }
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
                loadRecipes(query, filter)
            }
        }
    }

    private suspend fun loadRecipes(query: String, filter: ExploreRecipeFilter) {
        _uiState.update {
            it.copy(
                query = query,
                selectedFilter = filter,
                recipesState = UiState.Loading,
            )
        }

        recipeRepository.fetchRecipes(query = query, filter = filter)
            .onSuccess { recipes ->
                _uiState.update {
                    it.copy(recipesState = UiState.Success(recipes))
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        recipesState = UiState.Error(
                            error.message ?: "Could not load recipes",
                        ),
                    )
                }
            }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
