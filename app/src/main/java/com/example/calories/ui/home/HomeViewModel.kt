package com.example.calories.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.repository.ExerciseRepository
import com.example.calories.data.repository.FoodRepository
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.data.repository.WaterRepository
import com.example.calories.data.repository.WeightRepository
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.UserGoal
import com.example.calories.model.WeightEntry
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val foodRepository: FoodRepository,
    private val userGoalsRepository: UserGoalsRepository,
    private val weightRepository: WeightRepository,
    private val exerciseRepository: ExerciseRepository,
    private val waterRepository: WaterRepository,
) : ViewModel() {

    private val userId: String? get() = supabase.auth.currentUserOrNull()?.id

    private val _currentDate = MutableStateFlow(DateTimeUtils.today())
    val currentDate: StateFlow<LocalDate> = _currentDate

    private val _mealDetailsExpanded = MutableStateFlow(false)
    private val _mealFeedback = MutableStateFlow<Set<MealType>>(emptySet())
    private val _draftWeightKg = MutableStateFlow<Double?>(null)
    private var feedbackJob: Job? = null
    private var weightPersistJob: Job? = null

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<HomeNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    private val remoteSnapshot: StateFlow<Triple<List<FoodEntry>, UserGoal?, List<WeightEntry>>> =
        flowOf(userId)
            .flatMapLatest { id ->
                if (id == null) {
                    flowOf(Triple(emptyList(), null, emptyList()))
                } else {
                    combine(
                        foodRepository.observeFoodEntries(id),
                        userGoalsRepository.observeGoal(id),
                        weightRepository.observeWeightEntries(id),
                    ) { foods, goal, weights -> Triple(foods, goal, weights) }
                }
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                Triple(emptyList(), null, emptyList()),
            )

    private val exercisesForDate: StateFlow<List<ExerciseEntry>> =
        combine(flowOf(userId), _currentDate) { id, date -> id to date }
            .flatMapLatest { (id, date) ->
                if (id == null) {
                    flowOf(emptyList())
                } else {
                    exerciseRepository.observeExercisesForDate(id, date)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val waterForDate: StateFlow<Int> =
        combine(flowOf(userId), _currentDate) { id, date -> id to date }
            .flatMapLatest { (id, date) ->
                if (id == null) {
                    flowOf(0)
                } else {
                    waterRepository.observeTotalMlForDate(id, date)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val waterIntake: StateFlow<Int> = waterForDate

    val uiState: StateFlow<HomeUiState> = combine(
        combine(_currentDate, remoteSnapshot, waterForDate) { date, remote, water ->
            Triple(date, remote, water)
        },
        combine(exercisesForDate, _mealDetailsExpanded, _mealFeedback, _draftWeightKg) {
                exercises, detailsExpanded, feedback, draftWeight ->
            Quad(exercises, detailsExpanded, feedback, draftWeight)
        },
    ) { dateRemoteWater, extras ->
        val (date, remote, water) = dateRemoteWater
        val (foods, goal, weights) = remote
        val (exercises, detailsExpanded, feedback, draftWeight) = extras
        buildUiState(
            date = date,
            foods = foods,
            goal = goal,
            weights = weights,
            waterIntakeMl = water,
            exercises = exercises,
            mealDetailsExpanded = detailsExpanded,
            mealFeedback = feedback,
            draftWeightKg = draftWeight,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = buildUiState(
            date = _currentDate.value,
            foods = emptyList(),
            goal = null,
            weights = emptyList(),
            waterIntakeMl = 0,
            exercises = emptyList(),
            mealDetailsExpanded = false,
            mealFeedback = emptySet(),
            draftWeightKg = null,
        ),
    )

    val dailyGoal: StateFlow<Int> = uiState.map { it.dailyGoal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val totalEaten: StateFlow<Int> = uiState.map { it.totalEaten }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val totalBurned: StateFlow<Int> = uiState.map { it.totalBurned }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refresh()
    }

    fun previousDay() {
        _draftWeightKg.value = null
        weightPersistJob?.cancel()
        _currentDate.update { it.minusDays(1) }
    }

    fun nextDay() {
        _draftWeightKg.value = null
        weightPersistJob?.cancel()
        _currentDate.update { it.plusDays(1) }
    }

    fun refresh() {
        val id = userId ?: return
        viewModelScope.launch {
            runCatching { foodRepository.refresh(id) }
            runCatching { userGoalsRepository.refresh(id) }
            runCatching { weightRepository.refresh(id) }
            runCatching { exerciseRepository.refresh(id) }
            runCatching { waterRepository.refresh(id) }
        }
    }

    fun onAddMealClicked(mealType: MealType) {
        viewModelScope.launch {
            _navEvents.send(HomeNavEvent.OpenSearchFood(mealType))
        }
    }

    fun onMealFoodLogged(mealType: MealType) {
        _mealFeedback.update { it + mealType }
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(FEEDBACK_DURATION_MS)
            _mealFeedback.update { it - mealType }
        }
    }

    fun toggleMealDetails() {
        _mealDetailsExpanded.update { !it }
    }

    fun onMealFoodClicked(food: MealFoodItem, mealType: MealType) {
        viewModelScope.launch {
            _navEvents.send(
                HomeNavEvent.OpenFoodDetail(
                    foodId = food.id,
                    name = food.name,
                    calories = food.calories,
                    protein = food.protein,
                    carb = food.carb,
                    fat = food.fat,
                    servingGrams = food.servingGrams,
                    mealType = mealType,
                    viewOnly = true,
                ),
            )
        }
    }

    fun deleteMealFood(foodId: String) {
        viewModelScope.launch {
            runCatching { foodRepository.deleteFoodEntry(foodId) }
                .onFailure { e ->
                    _events.send(UiEvent.Message(e.message ?: "Could not delete food"))
                }
        }
    }

    fun onExerciseCardClicked() {
        viewModelScope.launch {
            _navEvents.send(HomeNavEvent.OpenExerciseLogger)
        }
    }

    fun addWater() {
        viewModelScope.launch {
            try {
                waterRepository.addWaterEntry(
                    amountMl = WATER_STEP_ML,
                    createdAt = DateTimeUtils.atNowOnDateIso(_currentDate.value),
                )
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not add water"))
            }
        }
    }

    fun removeWater() {
        viewModelScope.launch {
            try {
                waterRepository.removeLastForDate(
                    date = _currentDate.value,
                    amountMl = WATER_STEP_ML,
                )
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not remove water"))
            }
        }
    }

    fun adjustWeight(deltaKg: Double) {
        val current = _draftWeightKg.value
            ?: uiState.value.todayWeightKg
            ?: DEFAULT_WEIGHT_KG
        val next = ((current + deltaKg) * 10.0).let { round(it) / 10.0 }.coerceAtLeast(20.0)
        _draftWeightKg.value = next

        weightPersistJob?.cancel()
        weightPersistJob = viewModelScope.launch {
            delay(WEIGHT_PERSIST_DEBOUNCE_MS)
            persistDraftWeight()
        }
    }

    /** Flush any pending stepper value immediately (e.g. leaving Home for Progress). */
    fun flushPendingWeight() {
        val pending = _draftWeightKg.value ?: return
        weightPersistJob?.cancel()
        weightPersistJob = viewModelScope.launch {
            persistDraftWeight(pending)
        }
    }

    private suspend fun persistDraftWeight(override: Double? = null) {
        val toSave = override ?: _draftWeightKg.value ?: return
        try {
            weightRepository.upsertWeightForDate(
                weightKg = toSave,
                date = _currentDate.value,
            )
            if (_draftWeightKg.value == toSave) {
                _draftWeightKg.value = null
            }
        } catch (e: Exception) {
            _events.send(UiEvent.Message(e.message ?: "Could not save weight"))
        }
    }

    private fun buildUiState(
        date: LocalDate,
        foods: List<FoodEntry>,
        goal: UserGoal?,
        weights: List<WeightEntry>,
        waterIntakeMl: Int,
        exercises: List<ExerciseEntry>,
        mealDetailsExpanded: Boolean,
        mealFeedback: Set<MealType>,
        draftWeightKg: Double?,
    ): HomeUiState {
        val dayFoods = foods.filter { DateTimeUtils.isSameDay(it.createdAt, date) }
        val dailyGoal = goal?.dailyCalories ?: 0
        val macroTargets = macroTargetsFor(dailyGoal)
        val dayWeight = draftWeightKg ?: resolveWeightForDate(weights, goal, date)

        fun section(type: MealType, titleRes: Int): MealSection {
            val items = dayFoods
                .filter { it.mealType == type }
                .map { entry ->
                    MealFoodItem(
                        id = entry.id,
                        name = entry.name,
                        calories = entry.calories,
                        servingGrams = entry.servingGrams,
                        protein = entry.protein,
                        carb = entry.carb,
                        fat = entry.fat,
                    )
                }
            return MealSection(
                mealType = type,
                titleRes = titleRes,
                foods = items,
                showLoggedFeedback = type in mealFeedback,
            )
        }

        return HomeUiState(
            currentDate = date,
            currentDateLabel = DateTimeUtils.formatDdMmYyyy(date),
            dailyGoal = dailyGoal,
            totalEaten = dayFoods.sumOf { it.calories },
            totalBurned = exercises.sumOf { it.caloriesBurned.roundToInt() },
            waterIntakeMl = waterIntakeMl,
            waterGoalMl = WATER_GOAL_ML,
            waterStepMl = WATER_STEP_ML,
            protein = MacroProgress(
                currentGrams = dayFoods.sumOf { it.protein },
                targetGrams = macroTargets.protein,
            ),
            carbs = MacroProgress(
                currentGrams = dayFoods.sumOf { it.carb },
                targetGrams = macroTargets.carbs,
            ),
            fat = MacroProgress(
                currentGrams = dayFoods.sumOf { it.fat },
                targetGrams = macroTargets.fat,
            ),
            fiber = MacroProgress(
                currentGrams = 0.0,
                targetGrams = macroTargets.fiber,
            ),
            breakfast = section(MealType.BREAKFAST, R.string.meal_breakfast),
            lunch = section(MealType.LUNCH, R.string.meal_lunch),
            dinner = section(MealType.DINNER, R.string.meal_dinner),
            snacks = section(MealType.SNACK, R.string.meal_snacks),
            exercises = exercises.map {
                ExerciseLogItem(
                    id = it.id,
                    name = it.name,
                    caloriesBurned = it.caloriesBurned.roundToInt(),
                )
            },
            todayWeightKg = dayWeight,
            mealDetailsExpanded = mealDetailsExpanded,
        )
    }

    private fun resolveWeightForDate(
        weights: List<WeightEntry>,
        goal: UserGoal?,
        date: LocalDate,
    ): Double? {
        val sameDay = weights
            .filter { DateTimeUtils.isSameDay(it.recordedAt, date) }
            .maxByOrNull { it.recordedAt }
        if (sameDay != null) return sameDay.weightKg

        val prior = weights
            .mapNotNull { entry ->
                DateTimeUtils.toLocalDate(entry.recordedAt)?.let { d -> entry to d }
            }
            .filter { (_, d) -> !d.isAfter(date) }
            .maxByOrNull { (_, d) -> d }
            ?.first
        if (prior != null) return prior.weightKg

        return goal?.currentWeight?.takeIf { it > 0.0 }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    private data class MacroTargets(
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val fiber: Double,
    )

    private fun macroTargetsFor(dailyGoal: Int): MacroTargets {
        if (dailyGoal <= 0) {
            return MacroTargets(0.0, 0.0, 0.0, 0.0)
        }
        return MacroTargets(
            protein = dailyGoal * 0.30 / 4.0,
            carbs = dailyGoal * 0.40 / 4.0,
            fat = dailyGoal * 0.30 / 9.0,
            fiber = 30.0,
        )
    }

    private companion object {
        const val WATER_GOAL_ML = 2000
        const val WATER_STEP_ML = 250
        const val FEEDBACK_DURATION_MS = 1_500L
        const val WEIGHT_PERSIST_DEBOUNCE_MS = 450L
        const val DEFAULT_WEIGHT_KG = 60.0
    }
}
