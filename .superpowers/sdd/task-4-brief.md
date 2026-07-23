### Task 4: Ranking order + `selectHomeCallout` tests

**Files:**
- Modify: `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- Modify: `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

**Interfaces:**
- Consumes: all rules from Tasks 1â€“3
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
        // Leave 2 days missing? Full 7 days â€” no logging_gap. Add gap by dropping 2 weekdays:
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

- [ ] **Step 2: Run tests â€” ranking may fail until sort is applied**

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

