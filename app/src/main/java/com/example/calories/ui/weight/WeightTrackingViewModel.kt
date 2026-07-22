package com.example.calories.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.preferences.AppLanguage
import com.example.calories.data.preferences.AppPreferences
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.data.repository.ExerciseRepository
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.data.repository.WeightRepository
import com.example.calories.insights.InsightEngineInput
import com.example.calories.insights.InsightFoodDay
import com.example.calories.insights.InsightWeightPoint
import com.example.calories.insights.ProgressInsight
import com.example.calories.insights.ProgressInsightEngine
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.WeightEntry
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.CalorieCalculator
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

enum class NutritionPeriod {
    DAY,
    WEEK,
}

private const val COLLAPSED_HISTORY_COUNT = 3

data class DailyCaloriePoint(
    val date: LocalDate,
    val label: String,
    val consumedKcal: Int,
    val burnedKcal: Int,
)

data class MacroDistributionUi(
    val proteinGrams: Double = 0.0,
    val carbGrams: Double = 0.0,
    val fatGrams: Double = 0.0,
    val proteinPercent: Float = 0f,
    val carbPercent: Float = 0f,
    val fatPercent: Float = 0f,
    val centerLabel: String = "",
) {
    val hasData: Boolean
        get() = proteinGrams + carbGrams + fatGrams > 0.0
}

data class WeightUiState(
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    val entries: List<WeightEntry> = emptyList(),
    val isHistoryExpanded: Boolean = false,
    val nutritionPeriod: NutritionPeriod = NutritionPeriod.WEEK,
    val selectedDate: LocalDate = DateTimeUtils.today(),
    val periodLabel: String = "",
    val calorieTrend: List<DailyCaloriePoint> = emptyList(),
    val macroDistribution: MacroDistributionUi = MacroDistributionUi(),
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val insights: List<ProgressInsight> = emptyList(),
    val hasGoals: Boolean = false,
) {
    fun displayedHistoryEntries(): List<WeightEntry> {
        if (isHistoryExpanded || entries.size <= COLLAPSED_HISTORY_COUNT) return entries
        return entries.take(COLLAPSED_HISTORY_COUNT)
    }

    fun canToggleHistory(): Boolean = entries.size > COLLAPSED_HISTORY_COUNT
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeightTrackingViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val weightRepository: WeightRepository,
    private val userGoalsRepository: UserGoalsRepository,
    private val foodRepository: FoodRepository,
    private val exerciseRepository: ExerciseRepository,
    private val appPreferences: AppPreferences,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _nutritionPeriod = MutableStateFlow(NutritionPeriod.WEEK)
    val nutritionPeriod: StateFlow<NutritionPeriod> = _nutritionPeriod.asStateFlow()

    private val _selectedDate = MutableStateFlow(DateTimeUtils.today())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _isHistoryExpanded = MutableStateFlow(false)
    val isHistoryExpanded: StateFlow<Boolean> = _isHistoryExpanded.asStateFlow()

    val uiState: StateFlow<WeightUiState> = flowOf(userId)
        .flatMapLatest { id ->
            if (id == null) {
                combine(
                    appPreferences.unitSystem,
                    appPreferences.language,
                ) { unit, language ->
                    WeightUiState(unitSystem = unit, language = language)
                }
            } else {
                combine(
                    weightRepository.observeWeightEntries(id),
                    userGoalsRepository.observeGoal(id),
                    foodRepository.observeFoodEntries(id),
                    exerciseRepository.observeExerciseEntries(id),
                ) { entries, goal, foods, exercises ->
                    NutritionSourceData(
                        weightEntries = entries,
                        goalCurrentWeight = goal?.currentWeight,
                        goalTargetWeight = goal?.targetWeight,
                        dailyCalories = goal?.dailyCalories,
                        foods = foods,
                        exercises = exercises,
                    )
                }.combine(_nutritionPeriod) { source, period ->
                    source to period
                }.combine(_selectedDate) { (source, period), date ->
                    Triple(source, period, date)
                }.combine(
                    combine(appPreferences.unitSystem, appPreferences.language) { u, l -> u to l },
                ) { triple, prefs ->
                    val (source, period, date) = triple
                    val (unit, language) = prefs
                    buildUiState(source, period, date, unit, language)
                }
            }
        }
        .combine(_isHistoryExpanded) { state, expanded ->
            state.copy(isHistoryExpanded = expanded)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WeightUiState())

    init {
        refresh()
    }

    fun setNutritionPeriod(period: NutritionPeriod) {
        _nutritionPeriod.value = period
    }

    fun selectNutritionDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun toggleHistoryExpanded() {
        _isHistoryExpanded.value = !_isHistoryExpanded.value
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { weightRepository.refresh(id) }
            runCatching { userGoalsRepository.refresh(id) }
            runCatching { foodRepository.refresh(id) }
            runCatching { exerciseRepository.refresh(id) }
        }
    }

    /** @param weightDisplay Value entered in the active unit system; stored as kg. */
    fun addWeight(weightDisplay: Double) {
        val weightKg = UnitConverter.weightFromDisplay(
            weightDisplay,
            appPreferences.unitSystem.value,
        )
        viewModelScope.launch {
            try {
                weightRepository.upsertWeightForDate(
                    weightKg = weightKg,
                    date = DateTimeUtils.today(),
                )
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not log weight"))
            }
        }
    }

    private data class NutritionSourceData(
        val weightEntries: List<WeightEntry>,
        val goalCurrentWeight: Double?,
        val goalTargetWeight: Double?,
        val dailyCalories: Int?,
        val foods: List<FoodEntry>,
        val exercises: List<ExerciseEntry>,
    )

    private fun buildUiState(
        source: NutritionSourceData,
        period: NutritionPeriod,
        selectedDate: LocalDate,
        unitSystem: UnitSystem,
        language: AppLanguage,
    ): WeightUiState {
        val chronological = source.weightEntries.sortedBy { it.recordedAt }
        val days = daysForPeriod(period, selectedDate)
        val dailyCalories = source.dailyCalories
        val hasGoals = dailyCalories != null && dailyCalories > 0
        val insights = if (hasGoals) {
            buildInsights(source.foods, chronological, dailyCalories)
        } else {
            emptyList()
        }
        return WeightUiState(
            currentWeightKg = chronological.lastOrNull()?.weightKg
                ?: source.goalCurrentWeight,
            targetWeightKg = source.goalTargetWeight,
            entries = chronological.asReversed(),
            nutritionPeriod = period,
            selectedDate = selectedDate,
            periodLabel = formatPeriodLabel(period, days),
            calorieTrend = buildCalorieTrend(source.foods, source.exercises, days),
            macroDistribution = buildMacroDistribution(source.foods, period, days),
            unitSystem = unitSystem,
            language = language,
            insights = insights,
            hasGoals = hasGoals,
        )
    }

    companion object {
        private const val TREND_DAYS = 7
        private val dayLabelFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

        private fun daysForPeriod(
            period: NutritionPeriod,
            selectedDate: LocalDate,
        ): List<LocalDate> {
            return when (period) {
                NutritionPeriod.DAY -> listOf(selectedDate)
                NutritionPeriod.WEEK ->
                    (TREND_DAYS - 1 downTo 0).map { selectedDate.minusDays(it.toLong()) }
            }
        }

        private fun formatPeriodLabel(
            period: NutritionPeriod,
            days: List<LocalDate>,
        ): String {
            return when (period) {
                NutritionPeriod.DAY ->
                    DateTimeUtils.formatWeekdayMonthDay(days.first())
                NutritionPeriod.WEEK -> {
                    val start = days.first()
                    val end = days.last()
                    "${DateTimeUtils.formatMonthDay(start)} – ${DateTimeUtils.formatMonthDay(end)}"
                }
            }
        }

        private fun buildCalorieTrend(
            foods: List<FoodEntry>,
            exercises: List<ExerciseEntry>,
            days: List<LocalDate>,
        ): List<DailyCaloriePoint> {
            return days.map { date ->
                val consumed = foods
                    .filter { DateTimeUtils.isSameDay(it.createdAt, date) }
                    .sumOf { it.calories }
                val burned = exercises
                    .filter { DateTimeUtils.isSameDay(it.createdAt, date) }
                    .sumOf { it.caloriesBurned }
                    .roundToInt()
                DailyCaloriePoint(
                    date = date,
                    label = date.format(dayLabelFormatter),
                    consumedKcal = consumed,
                    burnedKcal = burned,
                )
            }
        }

        private fun buildInsights(
            foods: List<FoodEntry>,
            weights: List<WeightEntry>,
            dailyCalories: Int?,
            today: LocalDate = DateTimeUtils.today(),
        ): List<ProgressInsight> {
            if (dailyCalories == null || dailyCalories <= 0) return emptyList()
            val macros = CalorieCalculator.macroTargetsFor(dailyCalories)
            val foodDays = foods.mapNotNull { entry ->
                val date = DateTimeUtils.toLocalDate(entry.createdAt) ?: return@mapNotNull null
                InsightFoodDay(date, entry.calories, entry.protein)
            }
            val weightPoints = weights.mapNotNull { entry ->
                val date = DateTimeUtils.toLocalDate(entry.recordedAt) ?: return@mapNotNull null
                InsightWeightPoint(date, entry.weightKg)
            }
            return ProgressInsightEngine.evaluate(
                InsightEngineInput(
                    today = today,
                    dailyCalorieTarget = dailyCalories,
                    proteinTargetGrams = macros.proteinGrams,
                    foodDays = foodDays,
                    weights = weightPoints,
                ),
            )
        }

        private fun buildMacroDistribution(
            foods: List<FoodEntry>,
            period: NutritionPeriod,
            days: List<LocalDate>,
        ): MacroDistributionUi {
            val daySet = days.toSet()
            val periodFoods = foods.filter { entry ->
                DateTimeUtils.toLocalDate(entry.createdAt)?.let { it in daySet } == true
            }
            val totalProtein = periodFoods.sumOf { it.protein }
            val totalCarb = periodFoods.sumOf { it.carb }
            val totalFat = periodFoods.sumOf { it.fat }
            val totalMacros = totalProtein + totalCarb + totalFat
            val divisor = when (period) {
                NutritionPeriod.DAY -> 1.0
                NutritionPeriod.WEEK -> TREND_DAYS.toDouble()
            }

            fun percent(part: Double): Float =
                if (totalMacros > 0) ((part / totalMacros) * 100).toFloat() else 0f

            return MacroDistributionUi(
                proteinGrams = totalProtein / divisor,
                carbGrams = totalCarb / divisor,
                fatGrams = totalFat / divisor,
                proteinPercent = percent(totalProtein),
                carbPercent = percent(totalCarb),
                fatPercent = percent(totalFat),
                centerLabel = when (period) {
                    NutritionPeriod.DAY -> "Day"
                    NutritionPeriod.WEEK -> "7 days"
                },
            )
        }
    }
}
