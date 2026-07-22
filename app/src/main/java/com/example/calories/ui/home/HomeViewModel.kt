package com.example.calories.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.preferences.AppLanguage
import com.example.calories.data.preferences.AppPreferences
import com.example.calories.data.preferences.InsightPreferences
import com.example.calories.data.preferences.NotificationPreferences
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_CALORIES
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_CARBS
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_FAT
import com.example.calories.data.preferences.NotificationPreferences.Companion.METRIC_PROTEIN
import com.example.calories.data.preferences.UnitSystem
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
import com.example.calories.insights.InsightEngineInput
import com.example.calories.insights.InsightFoodDay
import com.example.calories.insights.InsightWeightPoint
import com.example.calories.insights.ProgressInsight
import com.example.calories.insights.ProgressInsightEngine
import com.example.calories.notifications.ReminderIds
import com.example.calories.notifications.ReminderScheduler
import com.example.calories.ui.common.UiEvent
import com.example.calories.util.CalorieCalculator
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    private val notificationPreferences: NotificationPreferences,
    private val insightPreferences: InsightPreferences,
    private val appPreferences: AppPreferences,
    private val reminderScheduler: ReminderScheduler,
    @ApplicationContext private val appContext: Context,
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
        combine(
            exercisesForDate,
            _mealDetailsExpanded,
            _mealFeedback,
            _draftWeightKg,
            notificationPreferences.intakeWarningsEnabled,
        ) { exercises, detailsExpanded, feedback, draftWeight, warningsEnabled ->
            Quint(exercises, detailsExpanded, feedback, draftWeight, warningsEnabled)
        },
        combine(appPreferences.unitSystem, appPreferences.language) { unit, language ->
            unit to language
        },
        insightPreferences.dismissedIds,
    ) { dateRemoteWater, extras, prefs, dismissedIds ->
        val (date, remote, water) = dateRemoteWater
        val (foods, goal, weights) = remote
        val (exercises, detailsExpanded, feedback, draftWeight, warningsEnabled) = extras
        val (unitSystem, language) = prefs
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
            intakeWarningsEnabled = warningsEnabled,
            unitSystem = unitSystem,
            language = language,
            dismissedIds = dismissedIds,
        )
    }.stateIn(
        scope = viewModelScope,
        // Stay hot while any tab is alive so Profile unit/language toggles re-emit immediately.
        started = SharingStarted.Eagerly,
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
            intakeWarningsEnabled = notificationPreferences.intakeWarningsEnabled.value,
            unitSystem = appPreferences.unitSystem.value,
            language = appPreferences.language.value,
            dismissedIds = insightPreferences.dismissedIds.value,
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
        observeIntakeNotifications()
    }

    /**
     * Watches [uiState] after it is built from food/goal data and runs intake checks
     * only when calorie or macro totals change (not on unrelated UI updates).
     */
    private fun observeIntakeNotifications() {
        viewModelScope.launch {
            uiState
                .distinctUntilChanged { old, new ->
                    old.totalEaten == new.totalEaten &&
                        old.dailyGoal == new.dailyGoal &&
                        old.protein == new.protein &&
                        old.carbs == new.carbs &&
                        old.fat == new.fat &&
                        old.intakeWarningsEnabled == new.intakeWarningsEnabled &&
                        old.currentDate == new.currentDate
                }
                .collect { state -> checkAndNotify(state) }
        }
    }

    private fun checkAndNotify(state: HomeUiState) {
        if (!state.intakeWarningsEnabled) return
        if (state.currentDate != DateTimeUtils.today()) return

        val todayKey = state.currentDate.toString()
        val title = appContext.getString(R.string.goal_exceeded_title)

        maybeNotifyExceeded(
            metricKey = METRIC_CALORIES,
            label = "Calories",
            currentGrams = state.totalEaten.toDouble(),
            targetGrams = state.dailyGoal.toDouble(),
            todayKey = todayKey,
            notificationId = ReminderIds.INTAKE_CALORIES,
            title = title,
            message = appContext.getString(R.string.goal_exceeded_calorie_message),
        )

        maybeNotifyExceeded(
            metricKey = METRIC_PROTEIN,
            label = "Protein",
            currentGrams = state.protein.currentGrams,
            targetGrams = state.protein.targetGrams,
            todayKey = todayKey,
            notificationId = ReminderIds.INTAKE_PROTEIN,
            title = title,
            message = appContext.getString(
                R.string.goal_exceeded_macro_message,
                appContext.getString(R.string.protein),
            ),
        )

        maybeNotifyExceeded(
            metricKey = METRIC_CARBS,
            label = "Carbs",
            currentGrams = state.carbs.currentGrams,
            targetGrams = state.carbs.targetGrams,
            todayKey = todayKey,
            notificationId = ReminderIds.INTAKE_CARBS,
            title = title,
            message = appContext.getString(
                R.string.goal_exceeded_macro_message,
                appContext.getString(R.string.carb),
            ),
        )

        maybeNotifyExceeded(
            metricKey = METRIC_FAT,
            label = "Fat",
            currentGrams = state.fat.currentGrams,
            targetGrams = state.fat.targetGrams,
            todayKey = todayKey,
            notificationId = ReminderIds.INTAKE_FAT,
            title = title,
            message = appContext.getString(
                R.string.goal_exceeded_macro_message,
                appContext.getString(R.string.fat),
            ),
        )
    }

    private fun maybeNotifyExceeded(
        metricKey: String,
        label: String,
        currentGrams: Double,
        targetGrams: Double,
        todayKey: String,
        notificationId: Int,
        title: String,
        message: String,
    ) {
        val exceeded = targetGrams > 0 && currentGrams > targetGrams
        val alreadyWarned = notificationPreferences.wasWarnedToday(metricKey, todayKey)
        Log.d(
            NOTIF_DEBUG_TAG,
            "$label: current=$currentGrams target=$targetGrams exceeded=$exceeded alreadyWarned=$alreadyWarned",
        )
        if (!exceeded || alreadyWarned) return

        Log.d(NOTIF_DEBUG_TAG, "$label limit exceeded — posting notification")
        runCatching {
            reminderScheduler.postNotification(
                notificationId = notificationId,
                title = title,
                message = message,
                channelId = ReminderScheduler.INTAKE_CHANNEL_ID,
            )
            notificationPreferences.markWarnedToday(metricKey, todayKey)
        }.onFailure { error ->
            Log.w(NOTIF_DEBUG_TAG, "Failed to post $label notification", error)
        }
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

    fun selectDate(date: LocalDate) {
        if (date == _currentDate.value) return
        _draftWeightKg.value = null
        weightPersistJob?.cancel()
        _currentDate.value = date
    }

    fun onNotificationsClicked() {
        viewModelScope.launch {
            _navEvents.send(HomeNavEvent.OpenNotificationSettings)
        }
    }

    fun dismissCallout() {
        val id = uiState.value.activeCallout?.id ?: return
        insightPreferences.dismiss(id)
    }

    fun onCalloutClicked() {
        viewModelScope.launch { _navEvents.send(HomeNavEvent.OpenProgressInsights) }
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

    fun expandMealDetails() {
        _mealDetailsExpanded.value = true
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
                    createdAt = food.createdAt,
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

    /**
     * @param deltaDisplay Step in the user's current unit system (0.1 kg or 0.1 lb).
     * Persisted value is always kilograms.
     */
    fun adjustWeight(deltaDisplay: Double) {
        val unit = uiState.value.unitSystem
        val currentKg = _draftWeightKg.value
            ?: uiState.value.todayWeightKg
            ?: DEFAULT_WEIGHT_KG
        val currentDisplay = UnitConverter.weightToDisplay(currentKg, unit)
        val nextDisplay = ((currentDisplay + deltaDisplay) * 10.0)
            .let { round(it) / 10.0 }
            .coerceAtLeast(UnitConverter.weightToDisplay(MIN_WEIGHT_KG, unit))
        _draftWeightKg.value = UnitConverter.weightFromDisplay(nextDisplay, unit)

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
        intakeWarningsEnabled: Boolean,
        unitSystem: UnitSystem,
        language: AppLanguage,
        dismissedIds: Set<String>,
    ): HomeUiState {
        insightPreferences.ensureCurrentWeek()
        val effectiveDismissedIds = insightPreferences.dismissedIds.value

        val dayFoods = foods.filter { DateTimeUtils.isSameDay(it.createdAt, date) }
        val dailyGoal = goal?.dailyCalories ?: 0
        val macroTargets = CalorieCalculator.macroTargetsFor(dailyGoal)
        val dayWeight = draftWeightKg ?: resolveWeightForDate(weights, goal, date)
        val activeCallout = if (goal == null || dailyGoal <= 0) {
            null
        } else {
            val insights = buildInsights(foods, weights, dailyGoal)
            ProgressInsightEngine.selectHomeCallout(insights, effectiveDismissedIds)
        }

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
                        createdAt = entry.createdAt,
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
            currentDateLabel = DateTimeUtils.formatWeekdayMonthDay(date),
            dailyGoal = dailyGoal,
            totalEaten = dayFoods.sumOf { it.calories },
            totalBurned = exercises.sumOf { it.caloriesBurned.roundToInt() },
            waterIntakeMl = waterIntakeMl,
            waterGoalMl = WATER_GOAL_ML,
            waterStepMl = WATER_STEP_ML,
            protein = MacroProgress(
                currentGrams = dayFoods.sumOf { it.protein },
                targetGrams = macroTargets.proteinGrams,
            ),
            carbs = MacroProgress(
                currentGrams = dayFoods.sumOf { it.carb },
                targetGrams = macroTargets.carbsGrams,
            ),
            fat = MacroProgress(
                currentGrams = dayFoods.sumOf { it.fat },
                targetGrams = macroTargets.fatGrams,
            ),
            fiber = MacroProgress(
                currentGrams = 0.0,
                targetGrams = macroTargets.fiberGrams,
            ),
            intakeWarningsEnabled = intakeWarningsEnabled,
            breakfast = section(MealType.BREAKFAST, R.string.meal_breakfast),
            lunch = section(MealType.LUNCH, R.string.meal_lunch),
            dinner = section(MealType.DINNER, R.string.meal_dinner),
            snacks = section(MealType.SNACKS, R.string.meal_snacks),
            exercises = exercises.map {
                ExerciseLogItem(
                    id = it.id,
                    name = it.name,
                    caloriesBurned = it.caloriesBurned.roundToInt(),
                )
            },
            todayWeightKg = dayWeight,
            unitSystem = unitSystem,
            language = language,
            mealDetailsExpanded = mealDetailsExpanded,
            activeCallout = activeCallout,
        )
    }

    private fun buildInsights(
        foods: List<FoodEntry>,
        weights: List<WeightEntry>,
        dailyCalories: Int,
        today: LocalDate = DateTimeUtils.today(),
    ): List<ProgressInsight> {
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

    private data class Quint<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
    )

    private companion object {
        const val NOTIF_DEBUG_TAG = "NOTIF_DEBUG"
        const val WATER_GOAL_ML = 2000
        const val WATER_STEP_ML = 250
        const val FEEDBACK_DURATION_MS = 1_500L
        const val WEIGHT_PERSIST_DEBOUNCE_MS = 450L
        const val DEFAULT_WEIGHT_KG = 60.0
        const val MIN_WEIGHT_KG = 20.0
    }
}
