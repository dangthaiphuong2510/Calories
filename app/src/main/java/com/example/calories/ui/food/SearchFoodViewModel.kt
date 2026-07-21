package com.example.calories.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodSearchFilter
import com.example.calories.model.FoodSearchTab
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class SearchFoodUiState(
    val mealType: MealType = MealType.SNACKS,
    val selectedDate: LocalDate = DateTimeUtils.today(),
    val dateLabelShort: String = "",
    val query: String = "",
    val selectedTab: FoodSearchTab = FoodSearchTab.RECENT,
    val selectedFilter: FoodSearchFilter = FoodSearchFilter.ALL,
    val results: List<FoodDictionaryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isEmpty: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchFoodViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _query = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(FoodSearchTab.RECENT)
    private val _selectedFilter = MutableStateFlow(FoodSearchFilter.ALL)
    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())

    private val _uiState = MutableStateFlow(SearchFoodUiState())
    val uiState: StateFlow<SearchFoodUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        observeFavorites()
        observeSearchInputs()
    }

    fun initialize(mealType: MealType, selectedDate: LocalDate) {
        val shortDate = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM", Locale.getDefault()))
        _uiState.update {
            it.copy(
                mealType = mealType,
                selectedDate = selectedDate,
                dateLabelShort = shortDate,
            )
        }
    }

    fun onQueryChanged(query: String) {
        _query.value = query
        _uiState.update { it.copy(query = query) }
    }

    fun clearQuery() {
        onQueryChanged("")
    }

    fun onTabSelected(tab: FoodSearchTab) {
        _selectedTab.value = tab
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun onFilterSelected(filter: FoodSearchFilter) {
        _selectedFilter.value = filter
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    private fun observeFavorites() {
        viewModelScope.launch {
            val id = userId ?: return@launch
            foodRepository.observeFavoriteFoodIds(id).collect { ids ->
                _favoriteIds.value = ids
            }
        }
    }

    private fun observeSearchInputs() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            combine(
                _query.debounce(DEBOUNCE_MS).distinctUntilChanged(),
                _selectedTab,
                _selectedFilter,
                _favoriteIds,
            ) { query, tab, filter, favoriteIds ->
                SearchInputs(query, tab, filter, favoriteIds)
            }.collectLatest { inputs ->
                _uiState.update { it.copy(isLoading = true) }
                val results = runCatching {
                    loadResults(
                        query = inputs.query,
                        filter = inputs.filter,
                        tab = inputs.tab,
                    )
                }.getOrElse { emptyList() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        isEmpty = results.isEmpty(),
                        query = inputs.query,
                        selectedTab = inputs.tab,
                        selectedFilter = inputs.filter,
                    )
                }
            }
        }
    }

    private suspend fun loadResults(
        query: String,
        filter: FoodSearchFilter,
        tab: FoodSearchTab,
    ): List<FoodDictionaryItem> {
        if (query.isNotBlank()) {
            return foodRepository.searchFoodDictionary(query, filter)
        }

        return when (tab) {
            FoodSearchTab.RECENT -> loadFromUserHistory(filter)
            FoodSearchTab.FAVORITES -> {
                val id = userId ?: return emptyList()
                foodRepository.getFavoriteFoods(id)
                    .let { applyLocalFilter(it, filter) }
            }
        }
    }

    private suspend fun loadFromUserHistory(filter: FoodSearchFilter): List<FoodDictionaryItem> {
        val id = userId ?: return emptyList()
        val entries = foodRepository.observeFoodEntries(id).first()
        return entries
            .asReversed()
            .distinctBy { it.name.lowercase(Locale.getDefault()) }
            .take(40)
            .map { entry ->
                FoodDictionaryItem(
                    id = entry.id.ifBlank { UUID.randomUUID().toString() },
                    name = entry.name,
                    calories = entry.calories.toLong(),
                    protein = entry.protein,
                    carb = entry.carb,
                    fat = entry.fat,
                )
            }
            .let { applyLocalFilter(it, filter) }
    }

    private fun applyLocalFilter(
        items: List<FoodDictionaryItem>,
        filter: FoodSearchFilter,
    ): List<FoodDictionaryItem> {
        return when (filter) {
            FoodSearchFilter.ALL -> items
            FoodSearchFilter.HIGH_PROTEIN -> items.filter { it.proteinGrams > 10 }
            FoodSearchFilter.LOW_CARBS -> items.filter { it.carbGrams < 10 }
            FoodSearchFilter.LOW_FAT -> items.filter { it.fatGrams < 3 }
        }
    }

    private data class SearchInputs(
        val query: String,
        val tab: FoodSearchTab,
        val filter: FoodSearchFilter,
        val favoriteIds: Set<String>,
    )

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
