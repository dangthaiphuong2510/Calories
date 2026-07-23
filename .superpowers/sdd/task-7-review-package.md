# Review package Task 7
BASE: 7e92423554e304ae29a72c605db81daf588894e6
HEAD: 103bf3d617eab8cf52b3d6af066f1b35a97bf251
## Commits
103bf3d feat(insights): show weekly insights on Progress tab

## Diff stat
 .../calories/ui/weight/ProgressInsightAdapter.kt   | 62 ++++++++++++++++++++++
 .../calories/ui/weight/WeightTrackingFragment.kt   | 15 ++++++
 .../calories/ui/weight/WeightTrackingViewModel.kt  | 54 ++++++++++++++++++-
 .../main/res/layout/fragment_weight_tracking.xml   | 27 ++++++++++
 app/src/main/res/layout/item_progress_insight.xml  | 39 ++++++++++++++
 5 files changed, 196 insertions(+), 1 deletion(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt b/app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt
new file mode 100644
index 0000000..e518f47
--- /dev/null
+++ b/app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt
@@ -0,0 +1,62 @@
+package com.example.calories.ui.weight
+
+import android.view.LayoutInflater
+import android.view.ViewGroup
+import androidx.recyclerview.widget.DiffUtil
+import androidx.recyclerview.widget.ListAdapter
+import androidx.recyclerview.widget.RecyclerView
+import com.example.calories.databinding.ItemProgressInsightBinding
+import com.example.calories.insights.InsightAction
+import com.example.calories.insights.ProgressInsight
+import com.example.calories.insights.ProgressInsightUiMapper
+
+class ProgressInsightAdapter(
+    private val onInsightClick: (ProgressInsight) -> Unit,
+) : ListAdapter<ProgressInsight, ProgressInsightAdapter.ViewHolder>(DiffCallback) {
+
+    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
+        val binding = ItemProgressInsightBinding.inflate(
+            LayoutInflater.from(parent.context),
+            parent,
+            false,
+        )
+        return ViewHolder(binding, onInsightClick)
+    }
+
+    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
+        holder.bind(getItem(position))
+    }
+
+    class ViewHolder(
+        private val binding: ItemProgressInsightBinding,
+        private val onInsightClick: (ProgressInsight) -> Unit,
+    ) : RecyclerView.ViewHolder(binding.root) {
+
+        fun bind(insight: ProgressInsight) {
+            val context = binding.root.context
+            binding.tvInsightTitle.setText(ProgressInsightUiMapper.titleRes(insight.id))
+            val bodyRes = ProgressInsightUiMapper.bodyRes(insight.id)
+            binding.tvInsightBody.text = if (insight.formatArgs.isEmpty()) {
+                context.getString(bodyRes)
+            } else {
+                context.getString(bodyRes, *insight.formatArgs.toTypedArray())
+            }
+            val isClickable = insight.action is InsightAction.OpenWeightLog
+            binding.root.isClickable = isClickable
+            binding.root.isFocusable = isClickable
+            if (isClickable) {
+                binding.root.setOnClickListener { onInsightClick(insight) }
+            } else {
+                binding.root.setOnClickListener(null)
+            }
+        }
+    }
+
+    private object DiffCallback : DiffUtil.ItemCallback<ProgressInsight>() {
+        override fun areItemsTheSame(oldItem: ProgressInsight, newItem: ProgressInsight): Boolean =
+            oldItem.id == newItem.id
+
+        override fun areContentsTheSame(oldItem: ProgressInsight, newItem: ProgressInsight): Boolean =
+            oldItem == newItem
+    }
+}
diff --git a/app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt b/app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt
index 597a7a4..a151edf 100644
--- a/app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt
+++ b/app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt
@@ -8,48 +8,53 @@ import android.widget.Toast
 import androidx.core.content.ContextCompat
 import androidx.core.view.isVisible
 import androidx.fragment.app.viewModels
 import androidx.recyclerview.widget.LinearLayoutManager
 import androidx.transition.AutoTransition
 import androidx.transition.TransitionManager
 import com.example.calories.R
 import com.example.calories.data.preferences.UnitSystem
 import com.example.calories.databinding.DialogAddWeightBinding
 import com.example.calories.databinding.FragmentWeightTrackingBinding
+import com.example.calories.insights.InsightAction
+import com.example.calories.insights.ProgressInsight
 import com.example.calories.ui.common.BaseFragment
 import com.example.calories.ui.common.UiEvent
 import com.example.calories.ui.common.collectLatestStarted
 import com.example.calories.util.UnitConverter
 import com.google.android.material.datepicker.MaterialDatePicker
 import com.google.android.material.dialog.MaterialAlertDialogBuilder
 import dagger.hilt.android.AndroidEntryPoint
 import java.time.Instant
 import java.time.ZoneOffset
 
 @AndroidEntryPoint
 class WeightTrackingFragment : BaseFragment<FragmentWeightTrackingBinding>() {
 
     private val viewModel: WeightTrackingViewModel by viewModels()
     private val adapter = WeightEntryAdapter()
+    private val insightAdapter = ProgressInsightAdapter(::onInsightClick)
     private var currentUnitSystem: UnitSystem = UnitSystem.METRIC
     private var lastHistoryExpanded: Boolean? = null
 
     override fun inflateBinding(
         inflater: LayoutInflater,
         container: ViewGroup?,
     ): FragmentWeightTrackingBinding =
         FragmentWeightTrackingBinding.inflate(inflater, container, false)
 
     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
         super.onViewCreated(view, savedInstanceState)
         binding.rvWeightEntries.layoutManager = LinearLayoutManager(requireContext())
         binding.rvWeightEntries.adapter = adapter
+        binding.rvInsights.layoutManager = LinearLayoutManager(requireContext())
+        binding.rvInsights.adapter = insightAdapter
         binding.fabLogWeight.setOnClickListener { showLogWeightDialog() }
         binding.btnToggleHistory.setOnClickListener { viewModel.toggleHistoryExpanded() }
         setupNutritionControls()
         observeViewModel()
     }
 
     override fun onResume() {
         super.onResume()
         viewModel.refresh()
     }
@@ -116,20 +121,24 @@ class WeightTrackingFragment : BaseFragment<FragmentWeightTrackingBinding>() {
             )
             MacroDistributionChartHelper.bind(
                 chart = binding.macroDistributionChart,
                 distribution = distribution,
                 proteinColor = proteinColor,
                 carbColor = carbColor,
                 fatColor = fatColor,
                 emptyMessage = getString(R.string.macro_legend_empty),
             )
             bindMacroLegend(distribution, proteinColor, carbColor, fatColor)
+
+            binding.sectionInsights.isVisible =
+                state.hasGoals && state.insights.isNotEmpty()
+            insightAdapter.submitList(state.insights)
         }
         viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
             when (event) {
                 is UiEvent.Message ->
                     Toast.makeText(requireContext(), event.text, Toast.LENGTH_LONG).show()
                 is UiEvent.MessageRes ->
                     Toast.makeText(requireContext(), event.resId, Toast.LENGTH_LONG).show()
             }
         }
     }
@@ -237,20 +246,26 @@ class WeightTrackingFragment : BaseFragment<FragmentWeightTrackingBinding>() {
             .build()
         picker.addOnPositiveButtonClickListener { millis ->
             val selected = Instant.ofEpochMilli(millis)
                 .atZone(ZoneOffset.UTC)
                 .toLocalDate()
             viewModel.selectNutritionDate(selected)
         }
         picker.show(parentFragmentManager, "nutrition_date_picker")
     }
 
+    private fun onInsightClick(insight: ProgressInsight) {
+        if (insight.action is InsightAction.OpenWeightLog) {
+            showLogWeightDialog()
+        }
+    }
+
     private fun showLogWeightDialog() {
         val dialogBinding = DialogAddWeightBinding.inflate(layoutInflater)
         dialogBinding.tilWeight.hint = getString(UnitConverter.weightLabelRes(currentUnitSystem))
         val dialog = MaterialAlertDialogBuilder(requireContext())
             .setView(dialogBinding.root)
             .create()
 
         dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
         dialogBinding.btnSave.setOnClickListener {
             val weight = dialogBinding.etWeight.text?.toString()?.toDoubleOrNull()
diff --git a/app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt b/app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt
index 127bb21..fa61087 100644
--- a/app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt
+++ b/app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt
@@ -2,24 +2,30 @@ package com.example.calories.ui.weight
 
 import androidx.lifecycle.ViewModel
 import androidx.lifecycle.viewModelScope
 import com.example.calories.data.preferences.AppLanguage
 import com.example.calories.data.preferences.AppPreferences
 import com.example.calories.data.preferences.UnitSystem
 import com.example.calories.data.repository.ExerciseRepository
 import com.example.calories.data.repository.FoodRepository
 import com.example.calories.data.repository.UserGoalsRepository
 import com.example.calories.data.repository.WeightRepository
+import com.example.calories.insights.InsightEngineInput
+import com.example.calories.insights.InsightFoodDay
+import com.example.calories.insights.InsightWeightPoint
+import com.example.calories.insights.ProgressInsight
+import com.example.calories.insights.ProgressInsightEngine
 import com.example.calories.model.ExerciseEntry
 import com.example.calories.model.FoodEntry
 import com.example.calories.model.WeightEntry
 import com.example.calories.ui.common.UiEvent
+import com.example.calories.util.CalorieCalculator
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
@@ -68,20 +74,22 @@ data class WeightUiState(
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
+    val insights: List<ProgressInsight> = emptyList(),
+    val hasGoals: Boolean = false,
 ) {
     fun displayedHistoryEntries(): List<WeightEntry> {
         if (isHistoryExpanded || entries.size <= COLLAPSED_HISTORY_COUNT) return entries
         return entries.take(COLLAPSED_HISTORY_COUNT)
     }
 
     fun canToggleHistory(): Boolean = entries.size > COLLAPSED_HISTORY_COUNT
 }
 
 @OptIn(ExperimentalCoroutinesApi::class)
@@ -118,21 +126,28 @@ class WeightTrackingViewModel @Inject constructor(
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
-                    NutritionSourceData(entries, goal?.currentWeight, goal?.targetWeight, foods, exercises)
+                    NutritionSourceData(
+                        weightEntries = entries,
+                        goalCurrentWeight = goal?.currentWeight,
+                        goalTargetWeight = goal?.targetWeight,
+                        dailyCalories = goal?.dailyCalories,
+                        foods = foods,
+                        exercises = exercises,
+                    )
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
@@ -185,45 +200,55 @@ class WeightTrackingViewModel @Inject constructor(
             } catch (e: Exception) {
                 _events.send(UiEvent.Message(e.message ?: "Could not log weight"))
             }
         }
     }
 
     private data class NutritionSourceData(
         val weightEntries: List<WeightEntry>,
         val goalCurrentWeight: Double?,
         val goalTargetWeight: Double?,
+        val dailyCalories: Int?,
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
+        val dailyCalories = source.dailyCalories
+        val hasGoals = dailyCalories != null && dailyCalories > 0
+        val insights = if (hasGoals) {
+            buildInsights(source.foods, chronological, dailyCalories)
+        } else {
+            emptyList()
+        }
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
+            insights = insights,
+            hasGoals = hasGoals,
         )
     }
 
     companion object {
         private const val TREND_DAYS = 7
         private val dayLabelFormatter: DateTimeFormatter =
             DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
 
         private fun daysForPeriod(
             period: NutritionPeriod,
@@ -266,20 +291,47 @@ class WeightTrackingViewModel @Inject constructor(
                     .roundToInt()
                 DailyCaloriePoint(
                     date = date,
                     label = date.format(dayLabelFormatter),
                     consumedKcal = consumed,
                     burnedKcal = burned,
                 )
             }
         }
 
+        private fun buildInsights(
+            foods: List<FoodEntry>,
+            weights: List<WeightEntry>,
+            dailyCalories: Int?,
+            today: LocalDate = DateTimeUtils.today(),
+        ): List<ProgressInsight> {
+            if (dailyCalories == null || dailyCalories <= 0) return emptyList()
+            val macros = CalorieCalculator.macroTargetsFor(dailyCalories)
+            val foodDays = foods.mapNotNull { entry ->
+                val date = DateTimeUtils.toLocalDate(entry.createdAt) ?: return@mapNotNull null
+                InsightFoodDay(date, entry.calories, entry.protein)
+            }
+            val weightPoints = weights.mapNotNull { entry ->
+                val date = DateTimeUtils.toLocalDate(entry.recordedAt) ?: return@mapNotNull null
+                InsightWeightPoint(date, entry.weightKg)
+            }
+            return ProgressInsightEngine.evaluate(
+                InsightEngineInput(
+                    today = today,
+                    dailyCalorieTarget = dailyCalories,
+                    proteinTargetGrams = macros.proteinGrams,
+                    foodDays = foodDays,
+                    weights = weightPoints,
+                ),
+            )
+        }
+
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
diff --git a/app/src/main/res/layout/fragment_weight_tracking.xml b/app/src/main/res/layout/fragment_weight_tracking.xml
index 792e240..79a26cc 100644
--- a/app/src/main/res/layout/fragment_weight_tracking.xml
+++ b/app/src/main/res/layout/fragment_weight_tracking.xml
@@ -21,20 +21,47 @@
             android:padding="20dp">
 
             <TextView
                 android:layout_width="wrap_content"
                 android:layout_height="wrap_content"
                 android:text="@string/progress_dashboard_title"
                 android:textColor="@color/text_primary"
                 android:textSize="24sp"
                 android:textStyle="bold" />
 
+            <LinearLayout
+                android:id="@+id/sectionInsights"
+                android:layout_width="match_parent"
+                android:layout_height="wrap_content"
+                android:layout_marginTop="20dp"
+                android:orientation="vertical"
+                android:visibility="gone"
+                tools:visibility="visible">
+
+                <TextView
+                    android:layout_width="wrap_content"
+                    android:layout_height="wrap_content"
+                    android:text="@string/insights_section_title"
+                    android:textColor="@color/text_primary"
+                    android:textSize="18sp"
+                    android:textStyle="bold" />
+
+                <androidx.recyclerview.widget.RecyclerView
+                    android:id="@+id/rvInsights"
+                    android:layout_width="match_parent"
+                    android:layout_height="wrap_content"
+                    android:layout_marginTop="12dp"
+                    android:nestedScrollingEnabled="false"
+                    tools:itemCount="2"
+                    tools:listitem="@layout/item_progress_insight" />
+            </LinearLayout>
+
             <!-- SECTION 1: Weight Progress & History -->
             <TextView
                 android:layout_width="wrap_content"
                 android:layout_height="wrap_content"
                 android:layout_marginTop="20dp"
                 android:text="@string/weight_progress"
                 android:textColor="@color/text_primary"
                 android:textSize="18sp"
                 android:textStyle="bold" />
 
diff --git a/app/src/main/res/layout/item_progress_insight.xml b/app/src/main/res/layout/item_progress_insight.xml
new file mode 100644
index 0000000..74bc286
--- /dev/null
+++ b/app/src/main/res/layout/item_progress_insight.xml
@@ -0,0 +1,39 @@
+<?xml version="1.0" encoding="utf-8"?>
+<com.google.android.material.card.MaterialCardView
+    xmlns:android="http://schemas.android.com/apk/res/android"
+    xmlns:app="http://schemas.android.com/apk/res-auto"
+    xmlns:tools="http://schemas.android.com/tools"
+    android:layout_width="match_parent"
+    android:layout_height="wrap_content"
+    android:layout_marginBottom="8dp"
+    app:cardBackgroundColor="?attr/colorSurface"
+    app:cardCornerRadius="16dp"
+    app:cardElevation="0dp"
+    app:strokeColor="@color/card_stroke"
+    app:strokeWidth="1dp">
+
+    <LinearLayout
+        android:layout_width="match_parent"
+        android:layout_height="wrap_content"
+        android:orientation="vertical"
+        android:padding="16dp">
+
+        <TextView
+            android:id="@+id/tvInsightTitle"
+            android:layout_width="match_parent"
+            android:layout_height="wrap_content"
+            android:textColor="@color/text_primary"
+            android:textSize="16sp"
+            android:textStyle="bold"
+            tools:text="Scale not moving" />
+
+        <TextView
+            android:id="@+id/tvInsightBody"
+            android:layout_width="match_parent"
+            android:layout_height="wrap_content"
+            android:layout_marginTop="4dp"
+            android:textColor="@color/text_secondary"
+            android:textSize="14sp"
+            tools:text="Body copy" />
+    </LinearLayout>
+</com.google.android.material.card.MaterialCardView>

