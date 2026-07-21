package com.example.calories.ui.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.RecipeRepository
import com.example.calories.model.Recipe
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.UiState
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

private const val DEFAULT_PORTION_GRAMS = 100.0

data class RecipeDetailUiState(
    val recipeState: UiState<Recipe> = UiState.Loading,
    val portionGrams: Double = DEFAULT_PORTION_GRAMS,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val scaledIngredients: List<ScaledRecipeIngredient> = emptyList(),
    val isSaving: Boolean = false,
    val isUnlocked: Boolean = false,
) {
    val totalMacros: Double get() = (protein + carb + fat).coerceAtLeast(0.01)
}

@HiltViewModel
class RecipeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recipeRepository: RecipeRepository,
    private val foodRepository: FoodRepository,
) : ViewModel() {

    private val recipeId: String = checkNotNull(savedStateHandle[ARG_RECIPE_ID])

    private val _recipeState = MutableStateFlow<UiState<Recipe>>(UiState.Loading)
    private val _portionGrams = MutableStateFlow(DEFAULT_PORTION_GRAMS)
    private val _isSaving = MutableStateFlow(false)
    private val _isUnlocked = MutableStateFlow(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _addedToMeal = Channel<Unit>(Channel.BUFFERED)
    val addedToMeal = _addedToMeal.receiveAsFlow()

    val uiState: StateFlow<RecipeDetailUiState> = combine(
        _recipeState,
        _portionGrams,
        _isSaving,
        _isUnlocked,
    ) { recipeState, portionGrams, isSaving, isUnlocked ->
        when (recipeState) {
            is UiState.Loading -> RecipeDetailUiState(
                recipeState = recipeState,
                portionGrams = portionGrams,
                isSaving = isSaving,
                isUnlocked = isUnlocked,
            )
            is UiState.Error -> RecipeDetailUiState(
                recipeState = recipeState,
                portionGrams = portionGrams,
                isSaving = isSaving,
                isUnlocked = isUnlocked,
            )
            is UiState.Success -> {
                val recipe = recipeState.data
                val scale = portionGrams / DEFAULT_PORTION_GRAMS
                val macros = recipe.macros
                RecipeDetailUiState(
                    recipeState = recipeState,
                    portionGrams = portionGrams,
                    calories = (recipe.totalKcal * scale).roundToInt(),
                    protein = (macros?.proteinG ?: 0.0) * scale,
                    carb = (macros?.carbsG ?: 0.0) * scale,
                    fat = (macros?.fatG ?: 0.0) * scale,
                    scaledIngredients = recipe.ingredients.map { it.scaled(scale) },
                    isSaving = isSaving,
                    isUnlocked = isUnlocked,
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RecipeDetailUiState(),
    )

    init {
        checkUnlockStatus()
        loadRecipe()
    }

    fun retry() {
        checkUnlockStatus()
        loadRecipe()
    }

    fun onPortionChanged(raw: String) {
        val value = raw.toDoubleOrNull() ?: return
        if (value <= 0.0) return
        _portionGrams.value = value
    }

    fun unlockRecipe() {
        recipeRepository.unlockRecipe(recipeId)
        _isUnlocked.value = true
    }

    fun addToMeal(mealType: MealType) {
        val state = uiState.value
        val recipe = (state.recipeState as? UiState.Success)?.data ?: return
        if (_isSaving.value) return

        viewModelScope.launch {
            _isSaving.value = true
            try {
                foodRepository.addFoodEntry(
                    name = recipe.name,
                    calories = state.calories,
                    protein = state.protein,
                    carb = state.carb,
                    fat = state.fat,
                    mealType = mealType,
                    servingGrams = state.portionGrams,
                    recordedAt = DateTimeUtils.atNoonIso(DateTimeUtils.today()),
                )
                _events.send(UiEvent.MessageRes(R.string.food_added_toast))
                _addedToMeal.send(Unit)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add meal"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun checkUnlockStatus() {
        _isUnlocked.value = recipeRepository.isRecipeUnlocked(recipeId)
    }

    private fun loadRecipe() {
        viewModelScope.launch {
            _recipeState.value = UiState.Loading
            recipeRepository.getRecipeById(recipeId)
                .onSuccess { recipe ->
                    _recipeState.value = UiState.Success(recipe)
                }
                .onFailure { error ->
                    _recipeState.value = UiState.Error(
                        error.message ?: "Could not load recipe",
                    )
                }
        }
    }

    companion object {
        const val ARG_RECIPE_ID = "recipe_id"
    }
}