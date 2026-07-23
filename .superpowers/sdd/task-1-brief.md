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

`ProgressInsightEngine.kt` (skeleton â€” only insufficient_data for now):

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

