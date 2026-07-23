# Review package Task 4
BASE: 08cba2152bd30cad53d1aa090ee8fe5828f4afaf
HEAD: 2658622bab7bcd23e600fbd866a897b4a6379e49
## Commits
2658622 feat(insights): lock ranking order and home callout selection

## Diff stat
 .../calories/insights/ProgressInsightEngine.kt     | 13 ++++-
 .../calories/insights/ProgressInsightEngineTest.kt | 65 ++++++++++++++++++++++
 2 files changed, 77 insertions(+), 1 deletion(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
index c527af3..96ecc9a 100644
--- a/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
+++ b/app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt
@@ -121,21 +121,32 @@ object ProgressInsightEngine {
                 if (delta <= ProgressInsightThresholds.FLAT_WEIGHT_MAX_ABS_DELTA_KG) {
                     insights += ProgressInsight(
                         id = ProgressInsightIds.PLATEAU_UNDER_TARGET,
                         severity = InsightSeverity.ACTIONABLE,
                         action = InsightAction.OpenWeightLog,
                     )
                 }
             }
         }
 
-        return insights.take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
+        val rank = listOf(
+            ProgressInsightIds.PLATEAU_UNDER_TARGET,
+            ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
+            ProgressInsightIds.PROTEIN_SHORTFALL,
+            ProgressInsightIds.LOGGING_GAP,
+            ProgressInsightIds.ON_TRACK_STREAK,
+        )
+        return insights
+            .sortedBy { insight ->
+                rank.indexOf(insight.id).let { if (it < 0) Int.MAX_VALUE else it }
+            }
+            .take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
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
index 959307b..bf9aa37 100644
--- a/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
+++ b/app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
@@ -126,11 +126,76 @@ class ProgressInsightEngineTest {
     }
 
     @Test
     fun plateau_skippedWhenFewerThanTwoWeights() {
         val foods = (6 downTo 0).map { offset ->
             InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0)
         }
         val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights = emptyList())).map { it.id }
         assertTrue(ProgressInsightIds.PLATEAU_UNDER_TARGET !in ids)
     }
+
+    @Test
+    fun ranking_prefersPlateauThenWeekendThenProteinThenGapThenOnTrack() {
+        val foods = (6 downTo 0).map { offset ->
+            val date = today.minusDays(offset.toLong())
+            val cal = when (date.dayOfWeek) {
+                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
+                else -> 1500
+            }
+            InsightFoodDay(date, cal, proteinGrams = 100.0)
+        }
+        val withGap = foods.filter {
+            it.date != today.minusDays(2) && it.date != today.minusDays(4)
+        }
+        val weights = listOf(
+            InsightWeightPoint(today.minusDays(10), 80.0),
+            InsightWeightPoint(today, 80.1),
+        )
+        val ids = ProgressInsightEngine.evaluate(baseInput(withGap, weights)).map { it.id }
+        val order = listOf(
+            ProgressInsightIds.PLATEAU_UNDER_TARGET,
+            ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
+            ProgressInsightIds.PROTEIN_SHORTFALL,
+            ProgressInsightIds.LOGGING_GAP,
+            ProgressInsightIds.ON_TRACK_STREAK,
+        )
+        val present = order.filter { it in ids }
+        assertEquals(present, ids.filter { it in order })
+        assertTrue(ids.size <= ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
+    }
+
+    @Test
+    fun selectHomeCallout_prefersActionableOverLoggingGap() {
+        val insights = listOf(
+            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
+            ProgressInsight(ProgressInsightIds.PROTEIN_SHORTFALL, InsightSeverity.ACTIONABLE),
+            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
+        )
+        val pick = ProgressInsightEngine.selectHomeCallout(insights, dismissedIds = emptySet())
+        assertEquals(ProgressInsightIds.PROTEIN_SHORTFALL, pick?.id)
+    }
+
+    @Test
+    fun selectHomeCallout_fallsBackToLoggingGapWhenNoActionable() {
+        val insights = listOf(
+            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
+            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
+        )
+        val pick = ProgressInsightEngine.selectHomeCallout(insights, emptySet())
+        assertEquals(ProgressInsightIds.LOGGING_GAP, pick?.id)
+    }
+
+    @Test
+    fun selectHomeCallout_skipsDismissedAndNeverReturnsPositive() {
+        val insights = listOf(
+            ProgressInsight(ProgressInsightIds.PROTEIN_SHORTFALL, InsightSeverity.ACTIONABLE),
+            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
+            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
+        )
+        val pick = ProgressInsightEngine.selectHomeCallout(
+            insights,
+            dismissedIds = setOf(ProgressInsightIds.PROTEIN_SHORTFALL),
+        )
+        assertEquals(ProgressInsightIds.LOGGING_GAP, pick?.id)
+    }
 }

