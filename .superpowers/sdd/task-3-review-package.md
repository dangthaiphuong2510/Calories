# Review package Task 3
BASE: cb4c8d5b74109b5d5bdaa73a8413950ff6d787c5
HEAD: 08cba2152bd30cad53d1aa090ee8fe5828f4afaf
## Commits
08cba21 feat(insights): add weekend spike and plateau rules

## Diff stat
 .../calories/insights/ProgressInsightEngine.kt     | 45 +++++++++++++++++++++-
 .../calories/insights/ProgressInsightEngineTest.kt | 40 +++++++++++++++++++
 2 files changed, 84 insertions(+), 1 deletion(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
index 19db4a0..c527af3 100644
--- a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
@@ -1,14 +1,13 @@
 package com.example.calories.insights
 
 import java.time.DayOfWeek
-import java.time.LocalDate
 import kotlin.math.abs
 
 object ProgressInsightEngine {
 
     fun evaluate(input: InsightEngineInput): List<ProgressInsight> {
         if (input.dailyCalorieTarget <= 0) return emptyList()
 
         val windowStart = input.today.minusDays(ProgressInsightThresholds.WINDOW_DAYS - 1)
         val foodsInWindow = input.foodDays
             .filter { it.date in windowStart..input.today }
@@ -78,20 +77,64 @@ object ProgressInsightEngine {
             day.calories.toDouble() in lo..hi
         }
         if (onTrackCount >= ProgressInsightThresholds.ON_TRACK_DAYS_NEEDED) {
             insights += ProgressInsight(
                 id = ProgressInsightIds.ON_TRACK_STREAK,
                 severity = InsightSeverity.POSITIVE,
                 formatArgs = listOf(onTrackCount.toString()),
             )
         }
 
+        // Weekend spike: compare averages inside the 7-day window
+        val weekendDays = foodsInWindow.filter {
+            it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY
+        }
+        val weekdayDays = foodsInWindow.filter {
+            it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY
+        }
+        if (weekendDays.isNotEmpty() && weekdayDays.isNotEmpty()) {
+            val weekendAvg = weekendDays.map { it.calories }.average()
+            val weekdayAvg = weekdayDays.map { it.calories }.average()
+            if (weekdayAvg > 0 &&
+                weekendAvg >= weekdayAvg * ProgressInsightThresholds.WEEKEND_SPIKE_RATIO
+            ) {
+                insights += ProgressInsight(
+                    id = ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
+                    severity = InsightSeverity.ACTIONABLE,
+                    action = InsightAction.OpenProgress,
+                )
+            }
+        }
+
+        // Plateau under target
+        val underSample = recentLogged.take(ProgressInsightThresholds.UNDER_TARGET_LOGGED_SAMPLE)
+        if (underSample.size >= ProgressInsightThresholds.UNDER_TARGET_LOGGED_SAMPLE) {
+            val underCount = underSample.count { it.calories < input.dailyCalorieTarget }
+            val weightStart =
+                input.today.minusDays(ProgressInsightThresholds.PLATEAU_WEIGHT_LOOKBACK_DAYS - 1)
+            val weightPoints = input.weights
+                .filter { it.date in weightStart..input.today }
+                .sortedBy { it.date }
+            if (underCount >= ProgressInsightThresholds.UNDER_TARGET_DAYS_NEEDED &&
+                weightPoints.size >= ProgressInsightThresholds.MIN_WEIGHT_POINTS_FOR_PLATEAU
+            ) {
+                val delta = abs(weightPoints.last().weightKg - weightPoints.first().weightKg)
+                if (delta <= ProgressInsightThresholds.FLAT_WEIGHT_MAX_ABS_DELTA_KG) {
+                    insights += ProgressInsight(
+                        id = ProgressInsightIds.PLATEAU_UNDER_TARGET,
+                        severity = InsightSeverity.ACTIONABLE,
+                        action = InsightAction.OpenWeightLog,
+                    )
+                }
+            }
+        }
+
         return insights.take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
     }
 
     fun selectHomeCallout(
         insights: List<ProgressInsight>,
         dismissedIds: Set<String>,
     ): ProgressInsight? {
         val visible = insights.filter { it.id !in dismissedIds }
         visible.firstOrNull { it.severity == InsightSeverity.ACTIONABLE }?.let { return it }
         return visible.firstOrNull { it.id == ProgressInsightIds.LOGGING_GAP }
diff --git a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
index 52c4374..959307b 100644
--- a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
+++ b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
@@ -1,15 +1,16 @@
 package com.example.calories.insights
 
 import org.junit.Assert.assertEquals
 import org.junit.Assert.assertTrue
 import org.junit.Test
+import java.time.DayOfWeek
 import java.time.LocalDate
 
 class ProgressInsightEngineTest {
 
     private val today = LocalDate.of(2026, 7, 22)
 
     @Test
     fun emptyFoods_returnsOnlyInsufficientData() {
         val result = ProgressInsightEngine.evaluate(
             InsightEngineInput(
@@ -86,11 +87,50 @@ class ProgressInsightEngineTest {
         val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
         assertTrue(ids.contains(ProgressInsightIds.PROTEIN_SHORTFALL))
     }
 
     @Test
     fun onTrackStreak_whenFiveDaysWithinTenPercentOfTarget() {
         val foods = sevenDays(calories = 2000, protein = 150.0)
         val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
         assertTrue(ids.contains(ProgressInsightIds.ON_TRACK_STREAK))
     }
+
+    @Test
+    fun weekendSpike_whenWeekendAvgAtLeast15PercentAboveWeekday() {
+        // Build MonΓÇôSun ending on today=Wed 2026-07-22: use explicit dates in the window
+        val foods = mutableListOf<InsightFoodDay>()
+        // last 7 days: Thu 16 ΓÇª Wed 22
+        for (offset in 6 downTo 0) {
+            val date = today.minusDays(offset.toLong())
+            val cal = when (date.dayOfWeek) {
+                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
+                else -> 1800
+            }
+            foods += InsightFoodDay(date, cal, 150.0)
+        }
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
+        assertTrue(ids.contains(ProgressInsightIds.WEEKEND_CALORIE_SPIKE))
+    }
+
+    @Test
+    fun plateau_whenUnderTargetOftenAndWeightFlat() {
+        val foods = (6 downTo 0).map { offset ->
+            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0) // under 2000
+        }
+        val weights = listOf(
+            InsightWeightPoint(today.minusDays(10), 80.0),
+            InsightWeightPoint(today, 80.2), // delta 0.2 < 0.4
+        )
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights)).map { it.id }
+        assertTrue(ids.contains(ProgressInsightIds.PLATEAU_UNDER_TARGET))
+    }
+
+    @Test
+    fun plateau_skippedWhenFewerThanTwoWeights() {
+        val foods = (6 downTo 0).map { offset ->
+            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0)
+        }
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights = emptyList())).map { it.id }
+        assertTrue(ProgressInsightIds.PLATEAU_UNDER_TARGET !in ids)
+    }
 }

