# Review package Task 2
BASE: abe11040f2fb9b19a7bd932f981b77c3ff431b04
HEAD: cb4c8d5b74109b5d5bdaa73a8413950ff6d787c5

## Commits
cb4c8d5 feat(insights): add logging gap, protein shortfall, on-track rules

## Diff stat
 .../calories/insights/ProgressInsightEngine.kt     | 56 +++++++++++++++++++++-
 .../calories/insights/ProgressInsightEngineTest.kt | 50 +++++++++++++++++++
 2 files changed, 104 insertions(+), 2 deletions(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
index 0062189..19db4a0 100644
--- a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
@@ -25,22 +25,74 @@ object ProgressInsightEngine {
         val foodDayCount = foodsInWindow.size
         if (foodDayCount < ProgressInsightThresholds.MIN_FOOD_LOG_DAYS) {
             return listOf(
                 ProgressInsight(
                     id = ProgressInsightIds.INSUFFICIENT_DATA,
                     severity = InsightSeverity.INFO,
                 ),
             )
         }
 
-        // Later tasks append rule detections here, then rank/take.
-        return emptyList()
+        val insights = mutableListOf<ProgressInsight>()
+
+        val missingDays = ProgressInsightThresholds.WINDOW_DAYS.toInt() - foodDayCount
+        if (missingDays >= ProgressInsightThresholds.LOGGING_GAP_MISSING_DAYS) {
+            insights += ProgressInsight(
+                id = ProgressInsightIds.LOGGING_GAP,
+                severity = InsightSeverity.INFO,
+                formatArgs = listOf(missingDays.toString()),
+                action = InsightAction.OpenProgress,
+            )
+        }
+
+        val loggedLookbackStart =
+            input.today.minusDays(ProgressInsightThresholds.LOGGED_LOOKBACK_DAYS - 1)
+        val recentLogged = input.foodDays
+            .filter { it.date in loggedLookbackStart..input.today }
+            .groupBy { it.date }
+            .map { (date, days) ->
+                InsightFoodDay(date, days.sumOf { it.calories }, days.sumOf { it.proteinGrams })
+            }
+            .sortedByDescending { it.date }
+
+        val proteinSample = recentLogged.take(ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE)
+        if (proteinSample.size >= ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE &&
+            input.proteinTargetGrams > 0
+        ) {
+            val shortDays = proteinSample.count { day ->
+                day.proteinGrams <
+                    input.proteinTargetGrams * ProgressInsightThresholds.PROTEIN_RATIO_THRESHOLD
+            }
+            if (shortDays >= ProgressInsightThresholds.PROTEIN_SHORTFALL_DAYS_NEEDED) {
+                insights += ProgressInsight(
+                    id = ProgressInsightIds.PROTEIN_SHORTFALL,
+                    severity = InsightSeverity.ACTIONABLE,
+                    action = InsightAction.OpenProgress,
+                )
+            }
+        }
+
+        val onTrackCount = foodsInWindow.count { day ->
+            val target = input.dailyCalorieTarget.toDouble()
+            val lo = target * (1.0 - ProgressInsightThresholds.ON_TRACK_TOLERANCE)
+            val hi = target * (1.0 + ProgressInsightThresholds.ON_TRACK_TOLERANCE)
+            day.calories.toDouble() in lo..hi
+        }
+        if (onTrackCount >= ProgressInsightThresholds.ON_TRACK_DAYS_NEEDED) {
+            insights += ProgressInsight(
+                id = ProgressInsightIds.ON_TRACK_STREAK,
+                severity = InsightSeverity.POSITIVE,
+                formatArgs = listOf(onTrackCount.toString()),
+            )
+        }
+
+        return insights.take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
     }
 
     fun selectHomeCallout(
         insights: List<ProgressInsight>,
         dismissedIds: Set<String>,
     ): ProgressInsight? {
         val visible = insights.filter { it.id !in dismissedIds }
         visible.firstOrNull { it.severity == InsightSeverity.ACTIONABLE }?.let { return it }
         return visible.firstOrNull { it.id == ProgressInsightIds.LOGGING_GAP }
     }
diff --git a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
index 9385713..52c4374 100644
--- a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
+++ b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
@@ -36,11 +36,61 @@ class ProgressInsightEngineTest {
             InsightEngineInput(
                 today = today,
                 dailyCalorieTarget = 2000,
                 proteinTargetGrams = 150.0,
                 foodDays = foods,
                 weights = emptyList(),
             ),
         )
         assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
     }
+
+    private fun baseInput(
+        foods: List<InsightFoodDay>,
+        weights: List<InsightWeightPoint> = emptyList(),
+        calories: Int = 2000,
+        protein: Double = 150.0,
+    ) = InsightEngineInput(
+        today = today,
+        dailyCalorieTarget = calories,
+        proteinTargetGrams = protein,
+        foodDays = foods,
+        weights = weights,
+    )
+
+    /** 7 consecutive days with food logs. */
+    private fun sevenDays(
+        calories: Int,
+        protein: Double = 150.0,
+    ): List<InsightFoodDay> =
+        (6 downTo 0).map { offset ->
+            InsightFoodDay(today.minusDays(offset.toLong()), calories, protein)
+        }
+
+    @Test
+    fun loggingGap_whenTwoOrMoreDaysMissingInWindow() {
+        // Only 5 of 7 days logged ΓåÆ 2 missing
+        val foods = listOf(0, 1, 2, 4, 5).map { offset ->
+            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 150.0)
+        }
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
+        assertTrue(ids.contains(ProgressInsightIds.LOGGING_GAP))
+        assertTrue(ProgressInsightIds.INSUFFICIENT_DATA !in ids)
+    }
+
+    @Test
+    fun proteinShortfall_whenFourOfLastFiveLoggedDaysBelow80Percent() {
+        val foods = (6 downTo 0).map { offset ->
+            // ~100g vs 150g target = 66% < 80%
+            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 100.0)
+        }
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
+        assertTrue(ids.contains(ProgressInsightIds.PROTEIN_SHORTFALL))
+    }
+
+    @Test
+    fun onTrackStreak_whenFiveDaysWithinTenPercentOfTarget() {
+        val foods = sevenDays(calories = 2000, protein = 150.0)
+        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
+        assertTrue(ids.contains(ProgressInsightIds.ON_TRACK_STREAK))
+    }
 }

