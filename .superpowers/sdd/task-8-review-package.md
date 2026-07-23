# Review package Task 8
BASE: 103bf3d617eab8cf52b3d6af066f1b35a97bf251
HEAD: 9db1fc26ab93d7eb77a9d39cd31cfbe3c58a0421
## Commits
9db1fc2 feat(insights): add dismissible Home callout linked to Progress

## Diff stat
 .../java/com/example/calories/ui/MainActivity.kt   |  8 ++++
 .../calories/ui/home/HomeDashboardModels.kt        |  3 ++
 .../com/example/calories/ui/home/HomeFragment.kt   | 24 ++++++++++
 .../com/example/calories/ui/home/HomeViewModel.kt  | 55 +++++++++++++++++++++-
 app/src/main/res/layout/fragment_home.xml          | 54 +++++++++++++++++++++
 5 files changed, 143 insertions(+), 1 deletion(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/ui/MainActivity.kt b/app/src/main/java/com/example/calories/ui/MainActivity.kt
index 332b5a9..4f3c36e 100644
--- a/app/src/main/java/com/example/calories/ui/MainActivity.kt
+++ b/app/src/main/java/com/example/calories/ui/MainActivity.kt
@@ -158,20 +158,28 @@ class MainActivity : BaseActivity(), EdgeToEdgeHost {
                     )
                     // Do not update bottom nav here ΓÇö the listener fired because the user selected it.
                     showTab(tag, updateBottomNav = false)
                     true
                 }
                 else -> false
             }
         }
     }
 
+    fun openProgressTab() {
+        supportFragmentManager.popBackStack(
+            CAMERA_BACK_STACK,
+            FragmentManager.POP_BACK_STACK_INCLUSIVE,
+        )
+        showTab(TAG_PROGRESS, updateBottomNav = true)
+    }
+
     private fun showTab(tag: String, updateBottomNav: Boolean = true) {
         if (tag == activeTabTag && isTabVisible(tag)) {
             if (updateBottomNav) selectBottomNavItem(tag)
             return
         }
 
         activeTabTag = tag
         val fm = supportFragmentManager
         val transaction = fm.beginTransaction()
 
diff --git a/app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt b/app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt
index 3a0912c..28e825b 100644
--- a/app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt
+++ b/app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt
@@ -1,14 +1,15 @@
 package com.example.calories.ui.home
 
 import com.example.calories.R
 import com.example.calories.data.preferences.AppLanguage
+import com.example.calories.insights.ProgressInsight
 import com.example.calories.data.preferences.UnitSystem
 import com.example.calories.model.enums.MealType
 import java.time.LocalDate
 
 data class MacroProgress(
     val currentGrams: Double = 0.0,
     val targetGrams: Double = 0.0,
 ) {
     val targetReached: Boolean
         get() = targetGrams > 0.0 && currentGrams >= targetGrams
@@ -67,20 +68,21 @@ data class HomeUiState(
     val intakeWarningsEnabled: Boolean = true,
     val breakfast: MealSection = MealSection(MealType.BREAKFAST, R.string.meal_breakfast),
     val lunch: MealSection = MealSection(MealType.LUNCH, R.string.meal_lunch),
     val dinner: MealSection = MealSection(MealType.DINNER, R.string.meal_dinner),
     val snacks: MealSection = MealSection(MealType.SNACKS, R.string.meal_snacks),
     val exercises: List<ExerciseLogItem> = emptyList(),
     val todayWeightKg: Double? = null,
     val unitSystem: UnitSystem = UnitSystem.METRIC,
     val language: AppLanguage = AppLanguage.ENGLISH,
     val mealDetailsExpanded: Boolean = false,
+    val activeCallout: ProgressInsight? = null,
 ) {
     val caloriesRemaining: Int
         get() = (dailyGoal - totalEaten + totalBurned).coerceAtLeast(0)
 
     val calorieProgressPercent: Int
         get() = if (dailyGoal <= 0) {
             0
         } else {
             ((totalEaten.toFloat() / dailyGoal) * 100f).toInt().coerceIn(0, 100)
         }
@@ -107,11 +109,12 @@ sealed interface HomeNavEvent {
         val calories: Int,
         val protein: Double,
         val carb: Double,
         val fat: Double,
         val servingGrams: Double,
         val mealType: MealType,
         val createdAt: String,
     ) : HomeNavEvent
     data object OpenExerciseLogger : HomeNavEvent
     data object OpenNotificationSettings : HomeNavEvent
+    data object OpenProgressInsights : HomeNavEvent
 }
diff --git a/app/src/main/java/com/example/calories/ui/home/HomeFragment.kt b/app/src/main/java/com/example/calories/ui/home/HomeFragment.kt
index bee032e..dcb842a 100644
--- a/app/src/main/java/com/example/calories/ui/home/HomeFragment.kt
+++ b/app/src/main/java/com/example/calories/ui/home/HomeFragment.kt
@@ -11,21 +11,23 @@ import android.widget.Toast
 import androidx.activity.result.contract.ActivityResultContracts
 import androidx.core.content.ContextCompat
 import androidx.fragment.app.Fragment
 import androidx.fragment.app.viewModels
 import com.example.calories.R
 import com.example.calories.databinding.FragmentHomeBinding
 import com.example.calories.databinding.ItemHomeExerciseBinding
 import com.example.calories.databinding.ItemHomeMealCircleBinding
 import com.example.calories.databinding.ItemHomeMealDetailSectionBinding
 import com.example.calories.databinding.ItemHomeMealFoodBinding
+import com.example.calories.insights.ProgressInsightUiMapper
 import com.example.calories.model.enums.MealType
+import com.example.calories.ui.MainActivity
 import com.example.calories.ui.camera.FoodAnalysisResultFragment
 import com.example.calories.ui.common.UiEvent
 import com.example.calories.ui.common.collectLatestStarted
 import com.example.calories.ui.exercise.ExerciseLoggerActivity
 import com.example.calories.ui.food.FoodDetailActivity
 import com.example.calories.ui.food.SearchFoodActivity
 import com.example.calories.ui.notifications.NotificationSettingsActivity
 import com.example.calories.util.DateTimeUtils
 import com.example.calories.util.UnitConverter
 import com.google.android.material.datepicker.MaterialDatePicker
@@ -119,30 +121,34 @@ class HomeFragment : Fragment() {
         }
 
         binding.sectionWater.btnAddWater.setOnClickListener { viewModel.addWater() }
         binding.sectionWater.btnRemoveWater.setOnClickListener { viewModel.removeWater() }
 
         binding.sectionExercise.root.setOnClickListener { viewModel.onExerciseCardClicked() }
         binding.sectionExercise.btnAddExercise.setOnClickListener { viewModel.onExerciseCardClicked() }
 
         binding.sectionWeight.btnWeightMinus.setOnClickListener { viewModel.adjustWeight(-0.1) }
         binding.sectionWeight.btnWeightPlus.setOnClickListener { viewModel.adjustWeight(+0.1) }
+
+        binding.cardInsightCallout.setOnClickListener { viewModel.onCalloutClicked() }
+        binding.btnDismissCallout.setOnClickListener { viewModel.dismissCallout() }
     }
 
     override fun onPause() {
         viewModel.flushPendingWeight()
         super.onPause()
     }
 
     private fun observeViewModel() {
         viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
             bindDateHeader(state)
+            bindCalloutCard(state)
             bindCalorieCard(state)
             bindMacrosCard(state)
             bindMealsSection(state)
             bindWaterCard(state)
             bindExerciseCard(state)
             bindWeightCard(state)
         }
         viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
             when (event) {
                 is UiEvent.Message ->
@@ -150,24 +156,42 @@ class HomeFragment : Fragment() {
                 is UiEvent.MessageRes ->
                     Toast.makeText(requireContext(), event.resId, Toast.LENGTH_SHORT).show()
             }
         }
         viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
             when (event) {
                 is HomeNavEvent.OpenSearchFood -> openSearchFood(event.mealType)
                 is HomeNavEvent.OpenFoodDetail -> openFoodDetail(event)
                 HomeNavEvent.OpenExerciseLogger -> openExerciseLogger()
                 HomeNavEvent.OpenNotificationSettings -> openNotificationSettings()
+                HomeNavEvent.OpenProgressInsights ->
+                    (activity as? MainActivity)?.openProgressTab()
             }
         }
     }
 
+    private fun bindCalloutCard(state: HomeUiState) {
+        val callout = state.activeCallout
+        if (callout == null) {
+            binding.cardInsightCallout.visibility = View.GONE
+            return
+        }
+        binding.cardInsightCallout.visibility = View.VISIBLE
+        binding.tvCalloutTitle.setText(ProgressInsightUiMapper.titleRes(callout.id))
+        val bodyRes = ProgressInsightUiMapper.bodyRes(callout.id)
+        binding.tvCalloutBody.text = if (callout.formatArgs.isEmpty()) {
+            getString(bodyRes)
+        } else {
+            getString(bodyRes, *callout.formatArgs.toTypedArray())
+        }
+    }
+
     private fun showDatePicker() {
         val current = viewModel.currentDate.value
         val selection = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
         val picker = MaterialDatePicker.Builder.datePicker()
             .setTitleText(R.string.select_date)
             .setSelection(selection)
             .setTheme(R.style.ThemeOverlay_Calories_MaterialCalendar)
             .build()
         picker.addOnPositiveButtonClickListener { millis ->
             val selected = Instant.ofEpochMilli(millis)
diff --git a/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt b/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
index c943288..0cf1106 100644
--- a/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
+++ b/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
@@ -1,35 +1,41 @@
 package com.example.calories.ui.home
 
 import android.content.Context
 import android.util.Log
 import androidx.lifecycle.ViewModel
 import androidx.lifecycle.viewModelScope
 import com.example.calories.R
 import com.example.calories.data.preferences.AppLanguage
 import com.example.calories.data.preferences.AppPreferences
+import com.example.calories.data.preferences.InsightPreferences
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
+import com.example.calories.insights.InsightEngineInput
+import com.example.calories.insights.InsightFoodDay
+import com.example.calories.insights.InsightWeightPoint
+import com.example.calories.insights.ProgressInsight
+import com.example.calories.insights.ProgressInsightEngine
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
@@ -57,20 +63,21 @@ import kotlin.math.roundToInt
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
+    private val insightPreferences: InsightPreferences,
     private val appPreferences: AppPreferences,
     private val reminderScheduler: ReminderScheduler,
     @ApplicationContext private val appContext: Context,
 ) : ViewModel() {
 
     private val userId: String? get() = supabase.auth.currentUserOrNull()?.id
 
     private val _currentDate = MutableStateFlow(DateTimeUtils.today())
     val currentDate: StateFlow<LocalDate> = _currentDate
 
@@ -138,56 +145,59 @@ class HomeViewModel @Inject constructor(
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
-    ) { dateRemoteWater, extras, prefs ->
+        insightPreferences.dismissedIds,
+    ) { dateRemoteWater, extras, prefs, dismissedIds ->
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
+            dismissedIds = dismissedIds,
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
+            dismissedIds = insightPreferences.dismissedIds.value,
         ),
     )
 
     val dailyGoal: StateFlow<Int> = uiState.map { it.dailyGoal }
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
     val totalEaten: StateFlow<Int> = uiState.map { it.totalEaten }
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
     val totalBurned: StateFlow<Int> = uiState.map { it.totalBurned }
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
 
@@ -327,20 +337,29 @@ class HomeViewModel @Inject constructor(
         weightPersistJob?.cancel()
         _currentDate.value = date
     }
 
     fun onNotificationsClicked() {
         viewModelScope.launch {
             _navEvents.send(HomeNavEvent.OpenNotificationSettings)
         }
     }
 
+    fun dismissCallout() {
+        val id = uiState.value.activeCallout?.id ?: return
+        insightPreferences.dismiss(id)
+    }
+
+    fun onCalloutClicked() {
+        viewModelScope.launch { _navEvents.send(HomeNavEvent.OpenProgressInsights) }
+    }
+
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
@@ -479,25 +498,32 @@ class HomeViewModel @Inject constructor(
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
+        dismissedIds: Set<String>,
     ): HomeUiState {
         val dayFoods = foods.filter { DateTimeUtils.isSameDay(it.createdAt, date) }
         val dailyGoal = goal?.dailyCalories ?: 0
         val macroTargets = CalorieCalculator.macroTargetsFor(dailyGoal)
         val dayWeight = draftWeightKg ?: resolveWeightForDate(weights, goal, date)
+        val activeCallout = if (goal == null || dailyGoal <= 0) {
+            null
+        } else {
+            val insights = buildInsights(foods, weights, dailyGoal)
+            ProgressInsightEngine.selectHomeCallout(insights, dismissedIds)
+        }
 
         fun section(type: MealType, titleRes: Int): MealSection {
             val items = dayFoods
                 .filter { it.mealType == type }
                 .map { entry ->
                     MealFoodItem(
                         id = entry.id,
                         name = entry.name,
                         calories = entry.calories,
                         servingGrams = entry.servingGrams,
@@ -549,20 +575,47 @@ class HomeViewModel @Inject constructor(
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
+            activeCallout = activeCallout,
+        )
+    }
+
+    private fun buildInsights(
+        foods: List<FoodEntry>,
+        weights: List<WeightEntry>,
+        dailyCalories: Int,
+        today: LocalDate = DateTimeUtils.today(),
+    ): List<ProgressInsight> {
+        val macros = CalorieCalculator.macroTargetsFor(dailyCalories)
+        val foodDays = foods.mapNotNull { entry ->
+            val date = DateTimeUtils.toLocalDate(entry.createdAt) ?: return@mapNotNull null
+            InsightFoodDay(date, entry.calories, entry.protein)
+        }
+        val weightPoints = weights.mapNotNull { entry ->
+            val date = DateTimeUtils.toLocalDate(entry.recordedAt) ?: return@mapNotNull null
+            InsightWeightPoint(date, entry.weightKg)
+        }
+        return ProgressInsightEngine.evaluate(
+            InsightEngineInput(
+                today = today,
+                dailyCalorieTarget = dailyCalories,
+                proteinTargetGrams = macros.proteinGrams,
+                foodDays = foodDays,
+                weights = weightPoints,
+            ),
         )
     }
 
     private fun resolveWeightForDate(
         weights: List<WeightEntry>,
         goal: UserGoal?,
         date: LocalDate,
     ): Double? {
         val sameDay = weights
             .filter { DateTimeUtils.isSameDay(it.recordedAt, date) }
diff --git a/app/src/main/res/layout/fragment_home.xml b/app/src/main/res/layout/fragment_home.xml
index 40c5af9..9568d1f 100644
--- a/app/src/main/res/layout/fragment_home.xml
+++ b/app/src/main/res/layout/fragment_home.xml
@@ -64,20 +64,74 @@
                 android:id="@+id/btnNotifications"
                 android:layout_width="@dimen/home_icon_button_size"
                 android:layout_height="@dimen/home_icon_button_size"
                 android:layout_marginStart="4dp"
                 android:background="?attr/selectableItemBackgroundBorderless"
                 android:contentDescription="@string/notification_settings_title"
                 android:src="@drawable/ic_notifications_24"
                 app:tint="?attr/colorOnSurface" />
         </LinearLayout>
 
+        <com.google.android.material.card.MaterialCardView
+            android:id="@+id/cardInsightCallout"
+            android:layout_width="match_parent"
+            android:layout_height="wrap_content"
+            android:layout_marginTop="@dimen/home_header_gap"
+            android:visibility="gone"
+            app:cardBackgroundColor="?attr/colorSurface"
+            app:cardCornerRadius="16dp"
+            app:cardElevation="0dp"
+            app:strokeColor="@color/card_stroke"
+            app:strokeWidth="1dp"
+            tools:visibility="visible">
+
+            <LinearLayout
+                android:layout_width="match_parent"
+                android:layout_height="wrap_content"
+                android:orientation="horizontal"
+                android:padding="12dp">
+
+                <LinearLayout
+                    android:layout_width="0dp"
+                    android:layout_height="wrap_content"
+                    android:layout_weight="1"
+                    android:orientation="vertical">
+
+                    <TextView
+                        android:id="@+id/tvCalloutTitle"
+                        android:layout_width="match_parent"
+                        android:layout_height="wrap_content"
+                        android:textColor="@color/text_primary"
+                        android:textStyle="bold"
+                        android:textSize="14sp" />
+
+                    <TextView
+                        android:id="@+id/tvCalloutBody"
+                        android:layout_width="match_parent"
+                        android:layout_height="wrap_content"
+                        android:layout_marginTop="2dp"
+                        android:textColor="@color/text_secondary"
+                        android:textSize="13sp"
+                        android:maxLines="3" />
+                </LinearLayout>
+
+                <ImageButton
+                    android:id="@+id/btnDismissCallout"
+                    android:layout_width="40dp"
+                    android:layout_height="40dp"
+                    android:background="?attr/selectableItemBackgroundBorderless"
+                    android:contentDescription="@string/insights_dismiss"
+                    android:src="@android:drawable/ic_menu_close_clear_cancel"
+                    app:tint="?attr/colorOnSurface" />
+            </LinearLayout>
+        </com.google.android.material.card.MaterialCardView>
+
         <include
             android:id="@+id/sectionCalories"
             layout="@layout/include_home_calorie_card"
             android:layout_width="match_parent"
             android:layout_height="wrap_content"
             android:layout_marginTop="@dimen/home_header_gap" />
 
         <include
             android:id="@+id/sectionMacros"
             layout="@layout/include_home_macros_card"

