### Task 3: Rules â€” `weekend_calorie_spike` + `plateau_under_target`

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
        // Build Monâ€“Sun ending on today=Wed 2026-07-22: use explicit dates in the window
        val foods = mutableListOf<InsightFoodDay>()
        // last 7 days: Thu 16 â€¦ Wed 22
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

