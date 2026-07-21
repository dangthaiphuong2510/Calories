package com.example.calories.ui.food

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.preferences.AppLanguage
import com.example.calories.data.preferences.AppPreferences
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.data.repository.FoodRepository
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class FoodDetailUiState(
    val name: String = "",
    val mealType: MealType = MealType.SNACKS,
    val selectedDate: LocalDate = DateTimeUtils.today(),
    /** Canonical portion in grams (always metric for persistence). */
    val portionGrams: Double = 100.0,
    /** Portion shown in the EditText for the active [unitSystem]. */
    val portionDisplay: Double = 100.0,
    val calories: Int = 0,
    val protein: Double = 0.0,
    val carb: Double = 0.0,
    val fat: Double = 0.0,
    val burnMinutesWalk: Int = 0,
    val burnMinutesRun: Int = 0,
    val burnMinutesCycle: Int = 0,
    val isEditMode: Boolean = false,
    val hasChanges: Boolean = false,
    val isSaving: Boolean = false,
    val isFavorite: Boolean = false,
    val showFavoriteButton: Boolean = false,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val language: AppLanguage = AppLanguage.ENGLISH,
) {
    val totalMacros: Double get() = (protein + carb + fat).coerceAtLeast(0.01)
    val proteinPercent: Float get() = (protein / totalMacros).toFloat()
    val carbPercent: Float get() = (carb / totalMacros).toFloat()
    val fatPercent: Float get() = (fat / totalMacros).toFloat()
    val portionHintRes: Int get() = UnitConverter.portionLabelRes(unitSystem)
}

@HiltViewModel
class FoodDetailViewModel @Inject constructor(
    private val foodRepository: FoodRepository,
    private val appPreferences: AppPreferences,
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _foodId = MutableStateFlow<String?>(null)
    private val _favoriteFoodId = MutableStateFlow<String?>(null)
    private val _isFavorite = MutableStateFlow(false)
    private val _createdAt = MutableStateFlow("")
    /** Nutrient values normalized per [BASE_PORTION_GRAMS] (100 g). */
    private val _baseCalories = MutableStateFlow(0)
    private val _baseProtein = MutableStateFlow(0.0)
    private val _baseCarb = MutableStateFlow(0.0)
    private val _baseFat = MutableStateFlow(0.0)
    /** Reference serving for log mode (dictionary item); always 100 g in edit mode. */
    private val _baseGrams = MutableStateFlow(BASE_PORTION_GRAMS)
    private val _portionGrams = MutableStateFlow(BASE_PORTION_GRAMS)
    private val _originalServingGrams = MutableStateFlow<Double?>(null)
    private val _name = MutableStateFlow("")
    private val _mealType = MutableStateFlow(MealType.SNACKS)
    private val _selectedDate = MutableStateFlow(DateTimeUtils.today())
    private val _isSaving = MutableStateFlow(false)

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _logged = Channel<MealType>(Channel.BUFFERED)
    val logged = _logged.receiveAsFlow()

    private val _updated = Channel<Unit>(Channel.BUFFERED)
    val updated = _updated.receiveAsFlow()

    val uiState: StateFlow<FoodDetailUiState> = combine(
        combine(
            combine(_name, _mealType, _selectedDate) { name, meal, date -> Triple(name, meal, date) },
            combine(_foodId, _favoriteFoodId, _isFavorite) { foodId, favoriteFoodId, isFavorite ->
                Triple(foodId, favoriteFoodId, isFavorite)
            },
        ) { identity, favorite ->
            FavoriteIdentity(
                name = identity.first,
                mealType = identity.second,
                selectedDate = identity.third,
                foodId = favorite.first,
                favoriteFoodId = favorite.second,
                isFavorite = favorite.third,
            )
        },
        combine(
            combine(_baseCalories, _baseProtein) { cals, p -> cals to p },
            combine(_baseCarb, _baseFat, _baseGrams, _portionGrams) { c, f, base, portion ->
                ScaleInputs(c, f, base, portion)
            },
        ) { calsProtein, scaleInputs ->
            NutrientBases(
                calories = calsProtein.first,
                protein = calsProtein.second,
                carb = scaleInputs.carb,
                fat = scaleInputs.fat,
                baseGrams = scaleInputs.baseGrams,
                portionGrams = scaleInputs.portionGrams,
            )
        },
        combine(
            _originalServingGrams,
            _isSaving,
            appPreferences.unitSystem,
            appPreferences.language,
        ) { originalServing, saving, unit, language ->
            PrefsBundle(originalServing, saving, unit, language)
        },
    ) { identity, bases, prefs ->
        val isEditMode = identity.foodId != null

        val ratio = if (isEditMode) {
            bases.portionGrams / BASE_PORTION_GRAMS
        } else if (bases.baseGrams <= 0.0) {
            0.0
        } else {
            bases.portionGrams / bases.baseGrams
        }

        val calories = (bases.calories * ratio).roundToInt()
        val protein = bases.protein * ratio
        val carb = bases.carb * ratio
        val fat = bases.fat * ratio

        val hasChanges = prefs.originalServing?.let { original ->
            bases.portionGrams != original
        } ?: false

        FoodDetailUiState(
            name = identity.name,
            mealType = identity.mealType,
            selectedDate = identity.selectedDate,
            portionGrams = bases.portionGrams,
            portionDisplay = UnitConverter.portionToDisplay(bases.portionGrams, prefs.unitSystem),
            calories = calories,
            protein = protein,
            carb = carb,
            fat = fat,
            burnMinutesWalk = burnMinutes(calories, WALK_KCAL_PER_MIN),
            burnMinutesRun = burnMinutes(calories, RUN_KCAL_PER_MIN),
            burnMinutesCycle = burnMinutes(calories, CYCLE_KCAL_PER_MIN),
            isEditMode = isEditMode,
            hasChanges = hasChanges,
            isSaving = prefs.saving,
            isFavorite = identity.isFavorite,
            showFavoriteButton = identity.favoriteFoodId != null,
            unitSystem = prefs.unitSystem,
            language = prefs.language,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoodDetailUiState())

    init {
        observeFavoriteState()
    }

    fun initialize(
        foodId: String?,
        favoriteFoodId: String?,
        name: String,
        calories: Int,
        protein: Double,
        carb: Double,
        fat: Double,
        servingGrams: Double,
        mealType: MealType,
        selectedDate: LocalDate,
        createdAt: String?,
    ) {
        _name.value = name
        _mealType.value = mealType
        _selectedDate.value = selectedDate
        _foodId.value = foodId
        _favoriteFoodId.value = favoriteFoodId
        _createdAt.value = createdAt ?: DateTimeUtils.atNoonIso(selectedDate)

        val grams = servingGrams.takeIf { it > 0.0 } ?: BASE_PORTION_GRAMS

        if (foodId != null) {
            // Normalize stored values to per-100 g bases for proportional scaling.
            val toBase = BASE_PORTION_GRAMS / grams
            _baseCalories.value = (calories * toBase).roundToInt()
            _baseProtein.value = protein * toBase
            _baseCarb.value = carb * toBase
            _baseFat.value = fat * toBase
            _baseGrams.value = BASE_PORTION_GRAMS
            _portionGrams.value = grams
            _originalServingGrams.value = grams
        } else {
            _baseCalories.value = calories
            _baseProtein.value = protein
            _baseCarb.value = carb
            _baseFat.value = fat
            _baseGrams.value = grams
            _portionGrams.value = BASE_PORTION_GRAMS
            _originalServingGrams.value = null
        }
    }

    fun toggleFavorite() {
        val id = userId ?: return
        val favoriteFoodId = _favoriteFoodId.value ?: return
        viewModelScope.launch {
            val isFavorite = _isFavorite.value
            foodRepository.setFavorite(
                userId = id,
                item = FoodDictionaryItem(
                    id = favoriteFoodId,
                    name = _name.value,
                    calories = _baseCalories.value.toLong(),
                    protein = _baseProtein.value,
                    carb = _baseCarb.value,
                    fat = _baseFat.value,
                ),
                isFavorite = !isFavorite,
            )
        }
    }

    private fun observeFavoriteState() {
        viewModelScope.launch {
            val id = userId ?: return@launch
            combine(_favoriteFoodId, foodRepository.observeFavoriteFoodIds(id)) {
                    favoriteFoodId, favoriteIds ->
                favoriteFoodId != null && favoriteFoodId in favoriteIds
            }.collect { isFavorite ->
                _isFavorite.value = isFavorite
            }
        }
    }

    /** Accepts the value typed by the user in the active unit system. */
    fun onPortionChanged(raw: String) {
        if (raw.isBlank()) return
        val displayValue = raw.toDoubleOrNull()
        if (displayValue == null || displayValue <= 0.0) {
            viewModelScope.launch {
                _events.send(UiEvent.MessageRes(R.string.invalid_portion_size))
            }
            return
        }
        _portionGrams.value = UnitConverter.portionFromDisplay(
            displayValue,
            appPreferences.unitSystem.value,
        )
    }

    fun logFood() {
        if (_foodId.value != null || _isSaving.value) return
        val state = uiState.value
        viewModelScope.launch {
            _isSaving.value = true
            try {
                foodRepository.addFoodEntry(
                    name = state.name,
                    calories = state.calories,
                    protein = state.protein,
                    carb = state.carb,
                    fat = state.fat,
                    mealType = state.mealType,
                    servingGrams = state.portionGrams,
                    recordedAt = DateTimeUtils.atNoonIso(state.selectedDate),
                )
                _events.send(UiEvent.MessageRes(R.string.food_added_toast))
                _logged.send(state.mealType)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add food"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveChanges() {
        val foodId = _foodId.value ?: return
        if (_isSaving.value || !uiState.value.hasChanges) return
        val state = uiState.value
        viewModelScope.launch {
            _isSaving.value = true
            try {
                foodRepository.updateFoodEntry(
                    FoodEntry(
                        id = foodId,
                        userId = "",
                        name = state.name,
                        calories = state.calories,
                        protein = state.protein,
                        carb = state.carb,
                        fat = state.fat,
                        mealType = state.mealType,
                        servingGrams = state.portionGrams,
                        createdAt = _createdAt.value,
                    ),
                )
                _originalServingGrams.value = state.portionGrams
                _events.send(UiEvent.MessageRes(R.string.food_updated_success))
                _updated.send(Unit)
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not update food"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    private fun burnMinutes(calories: Int, kcalPerMin: Double): Int {
        if (calories <= 0 || kcalPerMin <= 0.0) return 0
        return (calories / kcalPerMin).roundToInt().coerceAtLeast(1)
    }

    private data class FavoriteIdentity(
        val name: String,
        val mealType: MealType,
        val selectedDate: LocalDate,
        val foodId: String?,
        val favoriteFoodId: String?,
        val isFavorite: Boolean,
    )

    private data class ScaleInputs(
        val carb: Double,
        val fat: Double,
        val baseGrams: Double,
        val portionGrams: Double,
    )

    private data class NutrientBases(
        val calories: Int,
        val protein: Double,
        val carb: Double,
        val fat: Double,
        val baseGrams: Double,
        val portionGrams: Double,
    )

    private data class PrefsBundle(
        val originalServing: Double?,
        val saving: Boolean,
        val unitSystem: UnitSystem,
        val language: AppLanguage,
    )

    private companion object {
        const val BASE_PORTION_GRAMS = 100.0
        const val WALK_KCAL_PER_MIN = 4.0
        const val RUN_KCAL_PER_MIN = 10.0
        const val CYCLE_KCAL_PER_MIN = 8.0
    }
}
