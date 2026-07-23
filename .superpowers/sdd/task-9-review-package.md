# Review package Task 9
BASE: 9db1fc26ab93d7eb77a9d39cd31cfbe3c58a0421
HEAD: 4d0037b846ff8c02a1ab940c8f8ef8448324f593
## Commits
4d0037b fix(insights): refresh dismissals when ISO week changes

## Diff stat
 .../com/example/calories/data/preferences/InsightPreferences.kt    | 7 +++++++
 app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt    | 5 ++++-
 2 files changed, 11 insertions(+), 1 deletion(-)

## Full diff
diff --git a/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt b/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt
index 6baf02a..cdd5585 100644
--- a/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt
+++ b/app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt
@@ -35,20 +35,27 @@ class InsightPreferences @Inject constructor(
             .putStringSet(KEY_IDS, current)
             .apply()
         _dismissedIds.value = current.toSet()
     }
 
     fun clear() {
         prefs.edit().clear().apply()
         _dismissedIds.value = emptySet()
     }
 
+    fun ensureCurrentWeek() {
+        val current = readDismissedForCurrentWeek()
+        if (_dismissedIds.value != current) {
+            _dismissedIds.value = current
+        }
+    }
+
     private fun readDismissedForCurrentWeek(): Set<String> {
         val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
         val storedWeek = prefs.getString(KEY_WEEK, null)
         if (storedWeek != week) {
             prefs.edit().clear().apply()
             return emptySet()
         }
         return prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
     }
 
diff --git a/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt b/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
index 0cf1106..1ec2848 100644
--- a/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
+++ b/app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
@@ -500,29 +500,32 @@ class HomeViewModel @Inject constructor(
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
+        insightPreferences.ensureCurrentWeek()
+        val effectiveDismissedIds = insightPreferences.dismissedIds.value
+
         val dayFoods = foods.filter { DateTimeUtils.isSameDay(it.createdAt, date) }
         val dailyGoal = goal?.dailyCalories ?: 0
         val macroTargets = CalorieCalculator.macroTargetsFor(dailyGoal)
         val dayWeight = draftWeightKg ?: resolveWeightForDate(weights, goal, date)
         val activeCallout = if (goal == null || dailyGoal <= 0) {
             null
         } else {
             val insights = buildInsights(foods, weights, dailyGoal)
-            ProgressInsightEngine.selectHomeCallout(insights, dismissedIds)
+            ProgressInsightEngine.selectHomeCallout(insights, effectiveDismissedIds)
         }
 
         fun section(type: MealType, titleRes: Int): MealSection {
             val items = dayFoods
                 .filter { it.mealType == type }
                 .map { entry ->
                     MealFoodItem(
                         id = entry.id,
                         name = entry.name,
                         calories = entry.calories,

