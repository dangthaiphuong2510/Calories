### Task 2: Rules â€” `logging_gap`, `protein_shortfall`, `on_track_streak`

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
        // Only 5 of 7 days logged â†’ 2 missing
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

Replace the trailing `return emptyList()` path in `ProgressInsightEngine.evaluate` with rule collection (keep ranking simple for now â€” collect then sort by fixed order at end of Task 4; for this task return unsorted or already ranked â€” implement helpers):

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

Note: `logging_gap` uses days missing in the 7-day window (`7 - foodDayCount`). With exactly 5 logged days, missing = 2 â†’ fires.

- [ ] **Step 4: Run tests to verify they pass**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"`

Expected: PASS (all tests so far). If `onTrackStreak` also triggers `logging_gap` (0 missing), that is fine. If `proteinShortfall` testâ€™s 7 full days also triggers `on_track` / not gap â€” assert with `contains`, not exact list.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt
git commit -m "feat(insights): add logging gap, protein shortfall, on-track rules"
```

---

