# Progress Insights + Home Callouts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show rule-based weekly Progress Insights (up to 3 cards) and one dismissible Home callout so goal-chasers understand why the scale is or isn’t moving, using existing Room food/weight/goal data.

**Architecture:** A pure-Kotlin `ProgressInsightEngine` ranks fixed rules into `ProgressInsight` values (no Android/`R` deps). ViewModels map domain logs into engine input and expose UI state. `InsightPreferences` (SharedPreferences, matching `AppPreferences`) stores dismissed insight ids + ISO week key. UI maps insight ids → string resources via `ProgressInsightUiMapper`.

**Tech Stack:** Kotlin, JUnit unit tests, Hilt `@Inject`/`@Singleton`, View Binding, Material cards, existing Room repositories + `CalorieCalculator.macroTargetsFor`, SharedPreferences.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-22-progress-insights-design.md`
- Package root: `com.example.calories`
- Engine must stay Android-free (no `R`, Context, AndroidX)
- Persistence: SharedPreferences via `InsightPreferences` (project already uses SharedPreferences for prefs; do **not** add DataStore for this feature)
- No new Room tables / Supabase APIs / Gemini copy
- All user-facing strings in `values/strings.xml` **and** `values-vi/strings.xml`
- ViewModels never hardcode insight copy — use `ProgressInsightUiMapper` + string resources
- Do not change calorie/macro goals automatically
- YAGNI: exercise is **not** an engine input in v1 (no rule uses it)
- Run unit tests with: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`

## File structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/example/calories/insights/ProgressInsight.kt` | `ProgressInsight`, `InsightSeverity`, `InsightAction`, engine input DTOs |
| `app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt` | Locked numeric constants |
| `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt` | Pure rule evaluation + ranking + `selectHomeCallout` |
| `app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt` | ISO week key helper (pure) |
| `app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt` | Dismissed ids + week key persistence |
| `app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt` | `id` → title/body string res |
| `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt` | Engine unit tests |
| `app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt` | Week-key unit tests |
| Modify: `WeightTrackingViewModel.kt`, `WeightTrackingFragment.kt`, `fragment_weight_tracking.xml` | Progress insights section |
| Create: `item_progress_insight.xml`, `ProgressInsightAdapter.kt` | Insight cards |
| Modify: `HomeDashboardModels.kt`, `HomeViewModel.kt`, `HomeFragment.kt`, `fragment_home.xml` | Home callout |
| Modify: `MainActivity.kt` | Public `openProgressTab()` for Home tap |
| Modify: `LocalDataWiper.kt` | Clear insight prefs on wipe |
| Modify: `values/strings.xml`, `values-vi/strings.xml` | Copy |

---

### Task 1: Insight models, thresholds, engine skeleton + `insufficient_data`

**Files:**
- Create: `app/src/main/java/com/example/calories/insights/ProgressInsight.kt`
- Create: `app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt`
- Create: `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- Test: `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `ProgressInsightEngine.evaluate(input: InsightEngineInput): List<ProgressInsight>`
  - Insight ids as string constants on `ProgressInsightIds`
  - Input DTOs: `InsightEngineInput`, `InsightFoodDay`, `InsightWeightPoint`

- [ ] **Step 1: Write the failing tests for insufficient data / empty**

Create `ProgressInsightEngineTest.kt`:

```kotlin
package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressInsightEngineTest {

    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun emptyFoods_returnsOnlyInsufficientData() {
        val result = ProgressInsightEngine.evaluate(
            InsightEngineInput(
                today = today,
                dailyCalorieTarget = 2000,
                proteinTargetGrams = 150.0,
                foodDays = emptyList(),
                weights = listOf(
                    InsightWeightPoint(today.minusDays(3), 80.0),
                    InsightWeightPoint(today, 80.1),
                ),
            ),
        )
        assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
    }

    @Test
    fun fewerThanThreeFoodDays_returnsOnlyInsufficientData() {
        val foods = listOf(
            InsightFoodDay(today.minusDays(1), calories = 1800, proteinGrams = 100.0),
            InsightFoodDay(today, calories = 1800, proteinGrams = 100.0),
        )
        val result = ProgressInsightEngine.evaluate(
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: FAIL (classes not found / unresolved reference)

- [ ] **Step 3: Write minimal models + thresholds + engine**

`ProgressInsight.kt`:

```kotlin
package com.example.calories.insights

import java.time.LocalDate

object ProgressInsightIds {
    const val PLATEAU_UNDER_TARGET = "plateau_under_target"
    const val WEEKEND_CALORIE_SPIKE = "weekend_calorie_spike"
    const val PROTEIN_SHORTFALL = "protein_shortfall"
    const val LOGGING_GAP = "logging_gap"
    const val ON_TRACK_STREAK = "on_track_streak"
    const val INSUFFICIENT_DATA = "insufficient_data"
}

enum class InsightSeverity {
    ACTIONABLE,
    INFO,
    POSITIVE,
}

sealed class InsightAction {
    data object OpenProgress : InsightAction()
    data object OpenWeightLog : InsightAction()
}

data class ProgressInsight(
    val id: String,
    val severity: InsightSeverity,
    val formatArgs: List<String> = emptyList(),
    val action: InsightAction? = null,
)

/** One calendar day of aggregated food intake (already summed by the caller). */
data class InsightFoodDay(
    val date: LocalDate,
    val calories: Int,
    val proteinGrams: Double,
)

data class InsightWeightPoint(
    val date: LocalDate,
    val weightKg: Double,
)

data class InsightEngineInput(
    val today: LocalDate,
    val dailyCalorieTarget: Int,
    val proteinTargetGrams: Double,
    val foodDays: List<InsightFoodDay>,
    val weights: List<InsightWeightPoint>,
)
```

`ProgressInsightThresholds.kt`:

```kotlin
package com.example.calories.insights

object ProgressInsightThresholds {
    const val WINDOW_DAYS = 7L
    const val PLATEAU_WEIGHT_LOOKBACK_DAYS = 14L
    const val LOGGED_LOOKBACK_DAYS = 14L
    const val MIN_FOOD_LOG_DAYS = 3
    const val UNDER_TARGET_DAYS_NEEDED = 3
    const val UNDER_TARGET_LOGGED_SAMPLE = 5
    const val FLAT_WEIGHT_MAX_ABS_DELTA_KG = 0.4
    const val WEEKEND_SPIKE_RATIO = 1.15
    const val PROTEIN_RATIO_THRESHOLD = 0.80
    const val PROTEIN_SHORTFALL_DAYS_NEEDED = 4
    const val PROTEIN_LOGGED_SAMPLE = 5
    const val LOGGING_GAP_MISSING_DAYS = 2
    const val ON_TRACK_DAYS_NEEDED = 5
    const val ON_TRACK_TOLERANCE = 0.10
    const val MIN_WEIGHT_POINTS_FOR_PLATEAU = 2
    const val MAX_PROGRESS_INSIGHTS = 3
}
```

`ProgressInsightEngine.kt` (skeleton — only insufficient_data for now):

```kotlin
package com.example.calories.insights

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

object ProgressInsightEngine {

    fun evaluate(input: InsightEngineInput): List<ProgressInsight> {
        if (input.dailyCalorieTarget <= 0) return emptyList()

        val windowStart = input.today.minusDays(ProgressInsightThresholds.WINDOW_DAYS - 1)
        val foodsInWindow = input.foodDays
            .filter { it.date in windowStart..input.today }
            .groupBy { it.date }
            .map { (date, days) ->
                InsightFoodDay(
                    date = date,
                    calories = days.sumOf { it.calories },
                    proteinGrams = days.sumOf { it.proteinGrams },
                )
            }
            .sortedBy { it.date }

        val foodDayCount = foodsInWindow.size
        if (foodDayCount < ProgressInsightThresholds.MIN_FOOD_LOG_DAYS) {
            return listOf(
                ProgressInsight(
                    id = ProgressInsightIds.INSUFFICIENT_DATA,
                    severity = InsightSeverity.INFO,
                ),
            )
        }

        // Later tasks append rule detections here, then rank/take.
        return emptyList()
    }

    fun selectHomeCallout(
        insights: List<ProgressInsight>,
        dismissedIds: Set<String>,
    ): ProgressInsight? {
        val visible = insights.filter { it.id !in dismissedIds }
        visible.firstOrNull { it.severity == InsightSeverity.ACTIONABLE }?.let { return it }
        return visible.firstOrNull { it.id == ProgressInsightIds.LOGGING_GAP }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/ProgressInsight.kt app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
git commit -m "feat(insights): add engine skeleton with insufficient_data rule"
```

---

### Task 2: Rules — `logging_gap`, `protein_shortfall`, `on_track_streak`

**Files:**
- Modify: `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- Modify: `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine.evaluate` from Task 1
- Produces: same API; these three ids may appear in results when data is sufficient

- [ ] **Step 1: Write failing tests**

Append to `ProgressInsightEngineTest.kt`:

```kotlin
    private fun baseInput(
        foods: List<InsightFoodDay>,
        weights: List<InsightWeightPoint> = emptyList(),
        calories: Int = 2000,
        protein: Double = 150.0,
    ) = InsightEngineInput(
        today = today,
        dailyCalorieTarget = calories,
        proteinTargetGrams = protein,
        foodDays = foods,
        weights = weights,
    )

    /** 7 consecutive days with food logs. */
    private fun sevenDays(
        calories: Int,
        protein: Double = 150.0,
    ): List<InsightFoodDay> =
        (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), calories, protein)
        }

    @Test
    fun loggingGap_whenTwoOrMoreDaysMissingInWindow() {
        // Only 5 of 7 days logged → 2 missing
        val foods = listOf(0, 1, 2, 4, 5).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.LOGGING_GAP))
        assertTrue(ProgressInsightIds.INSUFFICIENT_DATA !in ids)
    }

    @Test
    fun proteinShortfall_whenFourOfLastFiveLoggedDaysBelow80Percent() {
        val foods = (6 downTo 0).map { offset ->
            // ~100g vs 150g target = 66% < 80%
            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 100.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.PROTEIN_SHORTFALL))
    }

    @Test
    fun onTrackStreak_whenFiveDaysWithinTenPercentOfTarget() {
        val foods = sevenDays(calories = 2000, protein = 150.0)
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.ON_TRACK_STREAK))
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: FAIL on new assertions (empty list from engine)

- [ ] **Step 3: Implement the three rules inside `evaluate`**

Replace the trailing `return emptyList()` path in `ProgressInsightEngine.evaluate` with rule collection (keep ranking simple for now — collect then sort by fixed order at end of Task 4; for this task return unsorted or already ranked — implement helpers):

```kotlin
        val insights = mutableListOf<ProgressInsight>()

        val missingDays = ProgressInsightThresholds.WINDOW_DAYS.toInt() - foodDayCount
        if (missingDays >= ProgressInsightThresholds.LOGGING_GAP_MISSING_DAYS) {
            insights += ProgressInsight(
                id = ProgressInsightIds.LOGGING_GAP,
                severity = InsightSeverity.INFO,
                formatArgs = listOf(missingDays.toString()),
                action = InsightAction.OpenProgress,
            )
        }

        val loggedLookbackStart =
            input.today.minusDays(ProgressInsightThresholds.LOGGED_LOOKBACK_DAYS - 1)
        val recentLogged = input.foodDays
            .filter { it.date in loggedLookbackStart..input.today }
            .groupBy { it.date }
            .map { (date, days) ->
                InsightFoodDay(date, days.sumOf { it.calories }, days.sumOf { it.proteinGrams })
            }
            .sortedByDescending { it.date }

        val proteinSample = recentLogged.take(ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE)
        if (proteinSample.size >= ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE &&
            input.proteinTargetGrams > 0
        ) {
            val shortDays = proteinSample.count { day ->
                day.proteinGrams <
                    input.proteinTargetGrams * ProgressInsightThresholds.PROTEIN_RATIO_THRESHOLD
            }
            if (shortDays >= ProgressInsightThresholds.PROTEIN_SHORTFALL_DAYS_NEEDED) {
                insights += ProgressInsight(
                    id = ProgressInsightIds.PROTEIN_SHORTFALL,
                    severity = InsightSeverity.ACTIONABLE,
                    action = InsightAction.OpenProgress,
                )
            }
        }

        val onTrackCount = foodsInWindow.count { day ->
            val target = input.dailyCalorieTarget.toDouble()
            val lo = target * (1.0 - ProgressInsightThresholds.ON_TRACK_TOLERANCE)
            val hi = target * (1.0 + ProgressInsightThresholds.ON_TRACK_TOLERANCE)
            day.calories.toDouble() in lo..hi
        }
        if (onTrackCount >= ProgressInsightThresholds.ON_TRACK_DAYS_NEEDED) {
            insights += ProgressInsight(
                id = ProgressInsightIds.ON_TRACK_STREAK,
                severity = InsightSeverity.POSITIVE,
                formatArgs = listOf(onTrackCount.toString()),
            )
        }

        return insights.take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
```

Note: `logging_gap` uses days missing in the 7-day window (`7 - foodDayCount`). With exactly 5 logged days, missing = 2 → fires.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS (all tests so far). If `onTrackStreak` also triggers `logging_gap` (0 missing), that is fine. If `proteinShortfall` test’s 7 full days also triggers `on_track` / not gap — assert with `contains`, not exact list.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
git commit -m "feat(insights): add logging gap, protein shortfall, on-track rules"
```

---

### Task 3: Rules — `weekend_calorie_spike` + `plateau_under_target`

**Files:**
- Modify: `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- Modify: `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

**Interfaces:**
- Consumes: engine from Task 2
- Produces: `WEEKEND_CALORIE_SPIKE`, `PLATEAU_UNDER_TARGET` detections

- [ ] **Step 1: Write failing tests**

```kotlin
    @Test
    fun weekendSpike_whenWeekendAvgAtLeast15PercentAboveWeekday() {
        // Build Mon–Sun ending on today=Wed 2026-07-22: use explicit dates in the window
        val foods = mutableListOf<InsightFoodDay>()
        // last 7 days: Thu 16 … Wed 22
        for (offset in 6 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val cal = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
                else -> 1800
            }
            foods += InsightFoodDay(date, cal, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.WEEKEND_CALORIE_SPIKE))
    }

    @Test
    fun plateau_whenUnderTargetOftenAndWeightFlat() {
        val foods = (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0) // under 2000
        }
        val weights = listOf(
            InsightWeightPoint(today.minusDays(10), 80.0),
            InsightWeightPoint(today, 80.2), // delta 0.2 < 0.4
        )
        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.PLATEAU_UNDER_TARGET))
    }

    @Test
    fun plateau_skippedWhenFewerThanTwoWeights() {
        val foods = (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights = emptyList())).map { it.id }
        assertTrue(ProgressInsightIds.PLATEAU_UNDER_TARGET !in ids)
    }
```

Add `import java.time.DayOfWeek`.

- [ ] **Step 2: Run tests to verify they fail**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: FAIL on weekend/plateau assertions

- [ ] **Step 3: Implement weekend + plateau helpers and add to `insights` list before `return`**

```kotlin
        // Weekend spike: compare averages inside the 7-day window
        val weekendDays = foodsInWindow.filter {
            it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY
        }
        val weekdayDays = foodsInWindow.filter {
            it.date.dayOfWeek != DayOfWeek.SATURDAY && it.date.dayOfWeek != DayOfWeek.SUNDAY
        }
        if (weekendDays.isNotEmpty() && weekdayDays.isNotEmpty()) {
            val weekendAvg = weekendDays.map { it.calories }.average()
            val weekdayAvg = weekdayDays.map { it.calories }.average()
            if (weekdayAvg > 0 &&
                weekendAvg >= weekdayAvg * ProgressInsightThresholds.WEEKEND_SPIKE_RATIO
            ) {
                insights += ProgressInsight(
                    id = ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
                    severity = InsightSeverity.ACTIONABLE,
                    action = InsightAction.OpenProgress,
                )
            }
        }

        // Plateau under target
        val underSample = recentLogged.take(ProgressInsightThresholds.UNDER_TARGET_LOGGED_SAMPLE)
        if (underSample.size >= ProgressInsightThresholds.UNDER_TARGET_LOGGED_SAMPLE) {
            val underCount = underSample.count { it.calories < input.dailyCalorieTarget }
            val weightStart =
                input.today.minusDays(ProgressInsightThresholds.PLATEAU_WEIGHT_LOOKBACK_DAYS - 1)
            val weightPoints = input.weights
                .filter { it.date in weightStart..input.today }
                .sortedBy { it.date }
            if (underCount >= ProgressInsightThresholds.UNDER_TARGET_DAYS_NEEDED &&
                weightPoints.size >= ProgressInsightThresholds.MIN_WEIGHT_POINTS_FOR_PLATEAU
            ) {
                val delta = abs(weightPoints.last().weightKg - weightPoints.first().weightKg)
                if (delta <= ProgressInsightThresholds.FLAT_WEIGHT_MAX_ABS_DELTA_KG) {
                    insights += ProgressInsight(
                        id = ProgressInsightIds.PLATEAU_UNDER_TARGET,
                        severity = InsightSeverity.ACTIONABLE,
                        action = InsightAction.OpenWeightLog,
                    )
                }
            }
        }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
git commit -m "feat(insights): add weekend spike and plateau rules"
```

---

### Task 4: Ranking order + `selectHomeCallout` tests

**Files:**
- Modify: `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- Modify: `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

**Interfaces:**
- Consumes: all rules from Tasks 1–3
- Produces: stable rank order; `selectHomeCallout` behavior locked by tests

- [ ] **Step 1: Write failing tests**

```kotlin
    @Test
    fun ranking_prefersPlateauThenWeekendThenProteinThenGapThenOnTrack() {
        // Craft input that can fire multiple; assert relative order among those present
        val foods = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val cal = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
                else -> 1500
            }
            InsightFoodDay(date, cal, proteinGrams = 100.0)
        }
        // Leave 2 days missing? Full 7 days — no logging_gap. Add gap by dropping 2 weekdays:
        val withGap = foods.filter {
            it.date != today.minusDays(2) && it.date != today.minusDays(4)
        }
        val weights = listOf(
            InsightWeightPoint(today.minusDays(10), 80.0),
            InsightWeightPoint(today, 80.1),
        )
        val ids = ProgressInsightEngine.evaluate(baseInput(withGap, weights)).map { it.id }
        val order = listOf(
            ProgressInsightIds.PLATEAU_UNDER_TARGET,
            ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
            ProgressInsightIds.PROTEIN_SHORTFALL,
            ProgressInsightIds.LOGGING_GAP,
            ProgressInsightIds.ON_TRACK_STREAK,
        )
        val present = order.filter { it in ids }
        assertEquals(present, ids.filter { it in order })
        assertTrue(ids.size <= ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
    }

    @Test
    fun selectHomeCallout_prefersActionableOverLoggingGap() {
        val insights = listOf(
            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
            ProgressInsight(ProgressInsightIds.PROTEIN_SHORTFALL, InsightSeverity.ACTIONABLE),
            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
        )
        val pick = ProgressInsightEngine.selectHomeCallout(insights, dismissedIds = emptySet())
        assertEquals(ProgressInsightIds.PROTEIN_SHORTFALL, pick?.id)
    }

    @Test
    fun selectHomeCallout_fallsBackToLoggingGapWhenNoActionable() {
        val insights = listOf(
            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
        )
        val pick = ProgressInsightEngine.selectHomeCallout(insights, emptySet())
        assertEquals(ProgressInsightIds.LOGGING_GAP, pick?.id)
    }

    @Test
    fun selectHomeCallout_skipsDismissedAndNeverReturnsPositive() {
        val insights = listOf(
            ProgressInsight(ProgressInsightIds.PROTEIN_SHORTFALL, InsightSeverity.ACTIONABLE),
            ProgressInsight(ProgressInsightIds.LOGGING_GAP, InsightSeverity.INFO),
            ProgressInsight(ProgressInsightIds.ON_TRACK_STREAK, InsightSeverity.POSITIVE),
        )
        val pick = ProgressInsightEngine.selectHomeCallout(
            insights,
            dismissedIds = setOf(ProgressInsightIds.PROTEIN_SHORTFALL),
        )
        assertEquals(ProgressInsightIds.LOGGING_GAP, pick?.id)
    }
```

- [ ] **Step 2: Run tests — ranking may fail until sort is applied**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: possibly FAIL on ranking order

- [ ] **Step 3: Apply rank sort before `take(MAX)`**

```kotlin
        val rank = listOf(
            ProgressInsightIds.PLATEAU_UNDER_TARGET,
            ProgressInsightIds.WEEKEND_CALORIE_SPIKE,
            ProgressInsightIds.PROTEIN_SHORTFALL,
            ProgressInsightIds.LOGGING_GAP,
            ProgressInsightIds.ON_TRACK_STREAK,
        )
        return insights
            .sortedBy { insight ->
                rank.indexOf(insight.id).let { if (it < 0) Int.MAX_VALUE else it }
            }
            .take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
```

Ensure `selectHomeCallout` already matches Task 1 implementation (no POSITIVE).

- [ ] **Step 4: Run all engine tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
git commit -m "feat(insights): lock ranking order and home callout selection"
```

---

### Task 5: ISO week keys + `InsightPreferences`

**Files:**
- Create: `app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt`
- Create: `app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt`
- Test: `app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt`
- Modify: `app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt`

**Interfaces:**
- Consumes: `java.time.LocalDate`
- Produces:
  - `InsightWeekKeys.isoWeekKey(date: LocalDate): String` → `"YYYY-Www"` (ISO)
  - `InsightPreferences.dismissedIdsForToday(): Set<String>`
  - `InsightPreferences.dismiss(insightId: String)`
  - `InsightPreferences.clear()`

- [ ] **Step 1: Write failing week-key tests**

```kotlin
package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InsightWeekKeysTest {
    @Test
    fun isoWeekKey_formatsYearAndWeek() {
        // 2026-07-22 is Wednesday of ISO week 30
        assertEquals("2026-W30", InsightWeekKeys.isoWeekKey(LocalDate.of(2026, 7, 22)))
    }
}
```

- [ ] **Step 2: Run test — expect FAIL**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"`

- [ ] **Step 3: Implement `InsightWeekKeys` + `InsightPreferences`**

```kotlin
package com.example.calories.insights

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

object InsightWeekKeys {
    fun isoWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }
}
```

```kotlin
package com.example.calories.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.calories.insights.InsightWeekKeys
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsightPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _dismissedIds = MutableStateFlow(readDismissedForCurrentWeek())
    val dismissedIds: StateFlow<Set<String>> = _dismissedIds.asStateFlow()

    fun dismiss(insightId: String) {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        val current = if (storedWeek == week) {
            prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        } else {
            mutableSetOf()
        }
        current += insightId
        prefs.edit()
            .putString(KEY_WEEK, week)
            .putStringSet(KEY_IDS, current)
            .apply()
        _dismissedIds.value = current.toSet()
    }

    fun clear() {
        prefs.edit().clear().apply()
        _dismissedIds.value = emptySet()
    }

    private fun readDismissedForCurrentWeek(): Set<String> {
        val week = InsightWeekKeys.isoWeekKey(DateTimeUtils.today())
        val storedWeek = prefs.getString(KEY_WEEK, null)
        if (storedWeek != week) {
            prefs.edit().clear().apply()
            return emptySet()
        }
        return prefs.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
    }

    companion object {
        const val PREFS_NAME = "insight_prefs"
        private const val KEY_WEEK = "week_key"
        private const val KEY_IDS = "dismissed_ids"
    }
}
```

Update `LocalDataWiper.wipeAll()` to also clear insight prefs (after notification prefs clear):

```kotlin
        context.getSharedPreferences(InsightPreferences.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
```

Add import for `InsightPreferences`.

- [ ] **Step 4: Run week-key tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/InsightWeekKeys.kt app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt app/src/test/java/com/example/calories/insights/InsightWeekKeysTest.kt app/src/main/java/com/example/calories/data/preferences/LocalDataWiper.kt
git commit -m "feat(insights): add week keys and dismiss preferences"
```

---

### Task 6: Strings (EN + VI) + `ProgressInsightUiMapper`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-vi/strings.xml`
- Create: `app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt`

**Interfaces:**
- Consumes: `ProgressInsight.id`, `formatArgs`
- Produces: `ProgressInsightUiMapper.titleRes(id)`, `bodyRes(id)` — Int resource ids

- [ ] **Step 1: Add English strings** (append near progress section in `values/strings.xml`)

```xml
    <string name="insights_section_title">Weekly insights</string>
    <string name="insights_insufficient_title">Keep logging</string>
    <string name="insights_insufficient_body">Log food for a few more days to unlock personal insights.</string>
    <string name="insights_plateau_title">Scale not moving</string>
    <string name="insights_plateau_body">Your logs show several days under target, but weight is nearly flat. Check portions or logging gaps.</string>
    <string name="insights_weekend_title">Weekend calorie spike</string>
    <string name="insights_weekend_body">Your logs show weekend intake meaningfully higher than weekdays.</string>
    <string name="insights_protein_title">Protein running low</string>
    <string name="insights_protein_body">Protein was under ~80% of target on most recent logged days.</string>
    <string name="insights_logging_gap_title">Missing food logs</string>
    <string name="insights_logging_gap_body">%1$s days in the last week have no food logged, so trends may be incomplete.</string>
    <string name="insights_on_track_title">Solid adherence</string>
    <string name="insights_on_track_body">You stayed near your calorie target on %1$s days this week.</string>
    <string name="insights_dismiss">Dismiss</string>
    <string name="insights_home_callout_cd">Progress insight</string>
```

- [ ] **Step 2: Add Vietnamese parity** in `values-vi/strings.xml` (same keys)

```xml
    <string name="insights_section_title">Nhận xét tuần này</string>
    <string name="insights_insufficient_title">Tiếp tục ghi nhật ký</string>
    <string name="insights_insufficient_body">Ghi thêm vài ngày thực phẩm để mở khóa nhận xét cá nhân.</string>
    <string name="insights_plateau_title">Cân không đổi</string>
    <string name="insights_plateau_body">Nhật ký cho thấy nhiều ngày dưới mục tiêu nhưng cân gần như đứng yên. Hãy kiểm tra khẩu phần hoặc ngày thiếu log.</string>
    <string name="insights_weekend_title">Cuối tuần cao calo</string>
    <string name="insights_weekend_body">Nhật ký cho thấy lượng ăn cuối tuần cao hơn rõ so với ngày thường.</string>
    <string name="insights_protein_title">Protein đang thấp</string>
    <string name="insights_protein_body">Protein dưới ~80% mục tiêu ở hầu hết các ngày gần đây có ghi nhật ký.</string>
    <string name="insights_logging_gap_title">Thiếu nhật ký món ăn</string>
    <string name="insights_logging_gap_body">%1$s ngày trong tuần qua chưa có nhật ký món ăn, xu hướng có thể chưa đủ.</string>
    <string name="insights_on_track_title">Duy trì tốt</string>
    <string name="insights_on_track_body">Bạn gần mục tiêu calo trong %1$s ngày tuần này.</string>
    <string name="insights_dismiss">Đóng</string>
    <string name="insights_home_callout_cd">Nhận xét tiến độ</string>
```

- [ ] **Step 3: Implement mapper**

```kotlin
package com.example.calories.insights

import androidx.annotation.StringRes
import com.example.calories.R

object ProgressInsightUiMapper {
    @StringRes
    fun titleRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_title
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_title
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_title
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_title
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_title
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_title
        else -> R.string.insights_section_title
    }

    @StringRes
    fun bodyRes(id: String): Int = when (id) {
        ProgressInsightIds.INSUFFICIENT_DATA -> R.string.insights_insufficient_body
        ProgressInsightIds.PLATEAU_UNDER_TARGET -> R.string.insights_plateau_body
        ProgressInsightIds.WEEKEND_CALORIE_SPIKE -> R.string.insights_weekend_body
        ProgressInsightIds.PROTEIN_SHORTFALL -> R.string.insights_protein_body
        ProgressInsightIds.LOGGING_GAP -> R.string.insights_logging_gap_body
        ProgressInsightIds.ON_TRACK_STREAK -> R.string.insights_on_track_body
        else -> R.string.insights_insufficient_body
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/main/java/com/example/calories/insights/ProgressInsightUiMapper.kt
git commit -m "feat(insights): add EN/VI copy and UI string mapper"
```

---

### Task 7: Wire Progress tab (ViewModel + layout + adapter + fragment)

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt`
- Modify: `app/src/main/res/layout/fragment_weight_tracking.xml`
- Create: `app/src/main/res/layout/item_progress_insight.xml`
- Create: `app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine.evaluate`, food/weight/goal flows already in VM, `CalorieCalculator.macroTargetsFor`
- Produces: `WeightUiState.insights: List<ProgressInsight>` (domain insights; fragment maps strings)

- [ ] **Step 1: Extend `WeightUiState`**

Add:

```kotlin
    val insights: List<ProgressInsight> = emptyList(),
    val hasGoals: Boolean = false,
```

- [ ] **Step 2: In `buildUiState` / combine path, compute insights when goal exists**

Helper (private in ViewModel or companion):

```kotlin
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
```

Pass `insights` and `hasGoals = source.goal exists / dailyCalories > 0` into `WeightUiState`. Confirm `DateTimeUtils.toLocalDate` exists (used already in this ViewModel); if only `isSameDay` exists, use the same parse pattern as `buildCalorieTrend`.

- [ ] **Step 3: Add insights section to `fragment_weight_tracking.xml`**

Insert **immediately after** the title `progress_dashboard_title` TextView and **before** the “Weight progress” section:

```xml
            <LinearLayout
                android:id="@+id/sectionInsights"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="20dp"
                android:orientation="vertical"
                android:visibility="gone"
                tools:visibility="visible">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/insights_section_title"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <androidx.recyclerview.widget.RecyclerView
                    android:id="@+id/rvInsights"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:nestedScrollingEnabled="false"
                    tools:itemCount="2"
                    tools:listitem="@layout/item_progress_insight" />
            </LinearLayout>
```

- [ ] **Step 4: Create `item_progress_insight.xml`**

Match existing Progress MaterialCard stroke/radius (16dp, `card_stroke`):

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="8dp"
    app:cardBackgroundColor="?attr/colorSurface"
    app:cardCornerRadius="16dp"
    app:cardElevation="0dp"
    app:strokeColor="@color/card_stroke"
    app:strokeWidth="1dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <TextView
            android:id="@+id/tvInsightTitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="16sp"
            android:textStyle="bold"
            tools:text="Scale not moving" />

        <TextView
            android:id="@+id/tvInsightBody"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textColor="@color/text_secondary"
            android:textSize="14sp"
            tools:text="Body copy" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 5: Adapter + fragment binding**

`ProgressInsightAdapter` binds title/body via `ProgressInsightUiMapper` and `formatArgs` (`getString(bodyRes, *args.toTypedArray())` when args non-empty).

In `WeightTrackingFragment`:
- `sectionInsights.isVisible = state.hasGoals && state.insights.isNotEmpty()`
- On insight click: if `action == OpenWeightLog` → existing `showLogWeightDialog()`; else no-op (already on Progress)

- [ ] **Step 6: Build/compile check**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/weight/WeightTrackingViewModel.kt app/src/main/java/com/example/calories/ui/weight/WeightTrackingFragment.kt app/src/main/java/com/example/calories/ui/weight/ProgressInsightAdapter.kt app/src/main/res/layout/fragment_weight_tracking.xml app/src/main/res/layout/item_progress_insight.xml
git commit -m "feat(insights): show weekly insights on Progress tab"
```

---

### Task 8: Wire Home callout + navigate to Progress

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeFragment.kt`
- Modify: `app/src/main/res/layout/fragment_home.xml`
- Modify: `app/src/main/java/com/example/calories/ui/MainActivity.kt`

**Interfaces:**
- Consumes: `ProgressInsightEngine`, `InsightPreferences`, same food/weight/goal streams as Home already has
- Produces: `HomeUiState.activeCallout: ProgressInsight?`; `HomeNavEvent.OpenProgressInsights`; `MainActivity.openProgressTab()`

- [ ] **Step 1: Extend models**

In `HomeUiState` add:

```kotlin
    val activeCallout: ProgressInsight? = null,
```

In `HomeNavEvent` add:

```kotlin
    data object OpenProgressInsights : HomeNavEvent
```

- [ ] **Step 2: Inject `InsightPreferences` into `HomeViewModel` and compute callout**

When building `HomeUiState` (same place macros/goals are applied):
1. If `goal == null || dailyGoal <= 0` → `activeCallout = null`
2. Else build `InsightEngineInput` from **all** observed foods/weights (not only selected day) — same aggregation as Progress
3. `val insights = ProgressInsightEngine.evaluate(...)`
4. `activeCallout = ProgressInsightEngine.selectHomeCallout(insights, insightPreferences.dismissedIds.value)`

Also combine `insightPreferences.dismissedIds` into the existing `uiState` combine so dismiss updates the banner.

Add:

```kotlin
fun dismissCallout() {
    val id = uiState.value.activeCallout?.id ?: return
    insightPreferences.dismiss(id)
}

fun onCalloutClicked() {
    viewModelScope.launch { _navEvents.send(HomeNavEvent.OpenProgressInsights) }
}
```

`uiState` is already a `StateFlow<HomeUiState>` — use `.value.activeCallout?.id` for dismiss.

- [ ] **Step 3: Layout — callout under date header, above calorie card**

In `fragment_home.xml` after the date/notifications row, before `sectionCalories`:

```xml
        <com.google.android.material.card.MaterialCardView
            android:id="@+id/cardInsightCallout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="@dimen/home_header_gap"
            android:visibility="gone"
            app:cardBackgroundColor="?attr/colorSurface"
            app:cardCornerRadius="16dp"
            app:cardElevation="0dp"
            app:strokeColor="@color/card_stroke"
            app:strokeWidth="1dp"
            tools:visibility="visible">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:padding="12dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:id="@+id/tvCalloutTitle"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:textColor="@color/text_primary"
                        android:textStyle="bold"
                        android:textSize="14sp" />

                    <TextView
                        android:id="@+id/tvCalloutBody"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="2dp"
                        android:textColor="@color/text_secondary"
                        android:textSize="13sp"
                        android:maxLines="3" />
                </LinearLayout>

                <ImageButton
                    android:id="@+id/btnDismissCallout"
                    android:layout_width="40dp"
                    android:layout_height="40dp"
                    android:background="?attr/selectableItemBackgroundBorderless"
                    android:contentDescription="@string/insights_dismiss"
                    android:src="@android:drawable/ic_menu_close_clear_cancel"
                    app:tint="?attr/colorOnSurface" />
            </LinearLayout>
        </com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 4: `HomeFragment` bind + click**

- Show card when `state.activeCallout != null`
- Set title/body via mapper
- `cardInsightCallout.setOnClickListener { viewModel.onCalloutClicked() }`
- `btnDismissCallout.setOnClickListener { viewModel.dismissCallout() }`
- Handle `HomeNavEvent.OpenProgressInsights` → `(activity as? MainActivity)?.openProgressTab()`

- [ ] **Step 5: `MainActivity.openProgressTab()`**

```kotlin
fun openProgressTab() {
    supportFragmentManager.popBackStack(
        CAMERA_BACK_STACK,
        FragmentManager.POP_BACK_STACK_INCLUSIVE,
    )
    showTab(TAG_PROGRESS, updateBottomNav = true)
}
```

(`showTab` is private — keep `openProgressTab` in MainActivity calling it.)

- [ ] **Step 6: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt app/src/main/java/com/example/calories/ui/home/HomeFragment.kt app/src/main/res/layout/fragment_home.xml app/src/main/java/com/example/calories/ui/MainActivity.kt
git commit -m "feat(insights): add dismissible Home callout linked to Progress"
```

---

### Task 9: Full unit suite + manual E2E checklist

**Files:**
- None new (verification only); optionally refresh dismissals when week rolls by ensuring `InsightPreferences` re-reads week on each `dismissedIds` access — already handled in `readDismissedForCurrentWeek` on init; add `fun refreshWeekIfNeeded()` called from HomeViewModel init / each evaluate if needed:

If `dismissedIds` can go stale across midnight week boundary while process lives, call from HomeViewModel when building state:

```kotlin
insightPreferences.ensureCurrentWeek()
```

Implement `ensureCurrentWeek()` as public wrapper around `readDismissedForCurrentWeek()` updating `_dismissedIds`.

**Interfaces:**
- Consumes: all prior tasks
- Produces: verified green unit suite + manual checklist passed by human

- [ ] **Step 1: Add `ensureCurrentWeek()` if missing and call from Home/Progress evaluate paths**

- [ ] **Step 2: Run full insight unit tests**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.*"
```

Expected: PASS

- [ ] **Step 3: Manual E2E** (human / device)

1. Seed ≥5 days food under target + 2 flat weights → Progress shows plateau; Home shows callout  
2. Tap callout → Progress tab opens  
3. Dismiss Home callout → kill app → reopen → still dismissed  
4. Change device date into next ISO week → dismissals cleared (banner can return)

- [ ] **Step 4: Commit any `ensureCurrentWeek` fix**

```bash
git add app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
git commit -m "fix(insights): refresh dismissals when ISO week changes"
```

(Skip commit if no code change.)

---

## Spec coverage self-check

| Spec requirement | Task |
|------------------|------|
| Progress Weekly Insights 1–3 cards | 7 |
| Home single dismissible callout | 8 |
| Pure `ProgressInsightEngine` | 1–4 |
| Rule catalog + ranking | 2–4 |
| Weight gating only on plateau | 3 |
| Insufficient data suppresses others | 1 |
| SharedPreferences dismiss + ISO week | 5 |
| EN + VI strings | 6 |
| No goals → hide | 7–8 (`hasGoals` / null callout) |
| Unit tests for engine | 1–4, 9 |
| Manual E2E | 9 |
| Out of scope (Fridge, adaptive goals, AI copy) | Not planned |

## Type consistency notes

- Insight id strings: only `ProgressInsightIds.*`
- Engine input: `InsightEngineInput` / `InsightFoodDay` / `InsightWeightPoint`
- Home selection: `ProgressInsightEngine.selectHomeCallout`
- UI strings: `ProgressInsightUiMapper.titleRes` / `bodyRes`
- Prefs: `InsightPreferences.PREFS_NAME = "insight_prefs"`
