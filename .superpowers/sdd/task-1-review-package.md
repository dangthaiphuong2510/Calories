# Review package Task 1
BASE: 08a994245f33edc9f33506dc9eea51cc8f173e15
HEAD: abe11040f2fb9b19a7bd932f981b77c3ff431b04

## Commits
abe1104 feat(insights): add engine skeleton with insufficient_data rule

## Diff stat
 .../example/calories/insights/ProgressInsight.kt   | 50 ++++++++++++++++++++++
 .../calories/insights/ProgressInsightEngine.kt     | 47 ++++++++++++++++++++
 .../calories/insights/ProgressInsightThresholds.kt | 20 +++++++++
 .../calories/insights/ProgressInsightEngineTest.kt | 46 ++++++++++++++++++++
 4 files changed, 163 insertions(+)

## Full diff
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsight.kt b/app/src/main/java/com/example/calories/insights/ProgressInsight.kt
new file mode 100644
index 0000000..c98956c
--- /dev/null
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsight.kt
@@ -0,0 +1,50 @@
+package com.example.calories.insights
+
+import java.time.LocalDate
+
+object ProgressInsightIds {
+    const val PLATEAU_UNDER_TARGET = "plateau_under_target"
+    const val WEEKEND_CALORIE_SPIKE = "weekend_calorie_spike"
+    const val PROTEIN_SHORTFALL = "protein_shortfall"
+    const val LOGGING_GAP = "logging_gap"
+    const val ON_TRACK_STREAK = "on_track_streak"
+    const val INSUFFICIENT_DATA = "insufficient_data"
+}
+
+enum class InsightSeverity {
+    ACTIONABLE,
+    INFO,
+    POSITIVE,
+}
+
+sealed class InsightAction {
+    data object OpenProgress : InsightAction()
+    data object OpenWeightLog : InsightAction()
+}
+
+data class ProgressInsight(
+    val id: String,
+    val severity: InsightSeverity,
+    val formatArgs: List<String> = emptyList(),
+    val action: InsightAction? = null,
+)
+
+/** One calendar day of aggregated food intake (already summed by the caller). */
+data class InsightFoodDay(
+    val date: LocalDate,
+    val calories: Int,
+    val proteinGrams: Double,
+)
+
+data class InsightWeightPoint(
+    val date: LocalDate,
+    val weightKg: Double,
+)
+
+data class InsightEngineInput(
+    val today: LocalDate,
+    val dailyCalorieTarget: Int,
+    val proteinTargetGrams: Double,
+    val foodDays: List<InsightFoodDay>,
+    val weights: List<InsightWeightPoint>,
+)
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
new file mode 100644
index 0000000..0062189
--- /dev/null
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
@@ -0,0 +1,47 @@
+package com.example.calories.insights
+
+import java.time.DayOfWeek
+import java.time.LocalDate
+import kotlin.math.abs
+
+object ProgressInsightEngine {
+
+    fun evaluate(input: InsightEngineInput): List<ProgressInsight> {
+        if (input.dailyCalorieTarget <= 0) return emptyList()
+
+        val windowStart = input.today.minusDays(ProgressInsightThresholds.WINDOW_DAYS - 1)
+        val foodsInWindow = input.foodDays
+            .filter { it.date in windowStart..input.today }
+            .groupBy { it.date }
+            .map { (date, days) ->
+                InsightFoodDay(
+                    date = date,
+                    calories = days.sumOf { it.calories },
+                    proteinGrams = days.sumOf { it.proteinGrams },
+                )
+            }
+            .sortedBy { it.date }
+
+        val foodDayCount = foodsInWindow.size
+        if (foodDayCount < ProgressInsightThresholds.MIN_FOOD_LOG_DAYS) {
+            return listOf(
+                ProgressInsight(
+                    id = ProgressInsightIds.INSUFFICIENT_DATA,
+                    severity = InsightSeverity.INFO,
+                ),
+            )
+        }
+
+        // Later tasks append rule detections here, then rank/take.
+        return emptyList()
+    }
+
+    fun selectHomeCallout(
+        insights: List<ProgressInsight>,
+        dismissedIds: Set<String>,
+    ): ProgressInsight? {
+        val visible = insights.filter { it.id !in dismissedIds }
+        visible.firstOrNull { it.severity == InsightSeverity.ACTIONABLE }?.let { return it }
+        return visible.firstOrNull { it.id == ProgressInsightIds.LOGGING_GAP }
+    }
+}
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt
new file mode 100644
index 0000000..899391a
--- /dev/null
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt
@@ -0,0 +1,20 @@
+package com.example.calories.insights
+
+object ProgressInsightThresholds {
+    const val WINDOW_DAYS = 7L
+    const val PLATEAU_WEIGHT_LOOKBACK_DAYS = 14L
+    const val LOGGED_LOOKBACK_DAYS = 14L
+    const val MIN_FOOD_LOG_DAYS = 3
+    const val UNDER_TARGET_DAYS_NEEDED = 3
+    const val UNDER_TARGET_LOGGED_SAMPLE = 5
+    const val FLAT_WEIGHT_MAX_ABS_DELTA_KG = 0.4
+    const val WEEKEND_SPIKE_RATIO = 1.15
+    const val PROTEIN_RATIO_THRESHOLD = 0.80
+    const val PROTEIN_SHORTFALL_DAYS_NEEDED = 4
+    const val PROTEIN_LOGGED_SAMPLE = 5
+    const val LOGGING_GAP_MISSING_DAYS = 2
+    const val ON_TRACK_DAYS_NEEDED = 5
+    const val ON_TRACK_TOLERANCE = 0.10
+    const val MIN_WEIGHT_POINTS_FOR_PLATEAU = 2
+    const val MAX_PROGRESS_INSIGHTS = 3
+}
diff --git a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
new file mode 100644
index 0000000..9385713
--- /dev/null
+++ b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
@@ -0,0 +1,46 @@
+package com.example.calories.insights
+
+import org.junit.Assert.assertEquals
+import org.junit.Assert.assertTrue
+import org.junit.Test
+import java.time.LocalDate
+
+class ProgressInsightEngineTest {
+
+    private val today = LocalDate.of(2026, 7, 22)
+
+    @Test
+    fun emptyFoods_returnsOnlyInsufficientData() {
+        val result = ProgressInsightEngine.evaluate(
+            InsightEngineInput(
+                today = today,
+                dailyCalorieTarget = 2000,
+                proteinTargetGrams = 150.0,
+                foodDays = emptyList(),
+                weights = listOf(
+                    InsightWeightPoint(today.minusDays(3), 80.0),
+                    InsightWeightPoint(today, 80.1),
+                ),
+            ),
+        )
+        assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
+    }
+
+    @Test
+    fun fewerThanThreeFoodDays_returnsOnlyInsufficientData() {
+        val foods = listOf(
+            InsightFoodDay(today.minusDays(1), calories = 1800, proteinGrams = 100.0),
+            InsightFoodDay(today, calories = 1800, proteinGrams = 100.0),
+        )
+        val result = ProgressInsightEngine.evaluate(
+            InsightEngineInput(
+                today = today,
+                dailyCalorieTarget = 2000,
+                proteinTargetGrams = 150.0,
+                foodDays = foods,
+                weights = emptyList(),
+            ),
+        )
+        assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
+    }
+}

