# Task 4 Report — Ranking order + `selectHomeCallout` tests

**Branch:** `feature/homeCallouts`  
**Base:** `08cba21` (Task 3 — weekend spike + plateau rules)  
**Commit:** `2658622` — feat(insights): lock ranking order and home callout selection  
**Date:** 2026-07-22

---

## Summary

Applied stable rank sort before `take(MAX_PROGRESS_INSIGHTS)` and locked `selectHomeCallout` behavior with four new tests.

| Priority | Rule ID |
|----------|---------|
| 1 | `plateau_under_target` |
| 2 | `weekend_calorie_spike` |
| 3 | `protein_shortfall` |
| 4 | `logging_gap` |
| 5 | `on_track_streak` |

`selectHomeCallout` unchanged from Task 1: prefers first ACTIONABLE (not dismissed), falls back to `logging_gap`, never returns POSITIVE.

---

## TDD Workflow

### Step 1 — Failing tests appended

Added to `ProgressInsightEngineTest.kt`:

- `ranking_prefersPlateauThenWeekendThenProteinThenGapThenOnTrack` — multi-rule fixture with gap, plateau weights, weekend spike, protein shortfall
- `selectHomeCallout_prefersActionableOverLoggingGap`
- `selectHomeCallout_fallsBackToLoggingGapWhenNoActionable`
- `selectHomeCallout_skipsDismissedAndNeverReturnsPositive`

### Step 2 — RED

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
BUILD FAILED — 12 tests, 1 failure (ranking_prefersPlateauThenWeekendThenProteinThenGapThenOnTrack)
```

### Step 3 — Implementation

Replaced bare `take(MAX)` with rank-index sort in `ProgressInsightEngine.evaluate`:

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

### Step 4 — GREEN

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
BUILD SUCCESSFUL — 12 tests, 0 failures
```

### Step 5 — Commit

Only Task 4 files staged and committed:

- `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

---

## Self-Review

### Correctness

- **Ranking test:** Fixture fires plateau, weekend spike, protein shortfall, and logging gap (on_track absent — no days within ±10% of target). MAX cap (3) drops logging_gap; top three match rank order.
- **selectHomeCallout:** Existing Task 1 implementation already matched all three callout tests — no engine changes needed for callout logic.
- **Unknown IDs:** Rules not in rank list sort to `Int.MAX_VALUE` (e.g. `insufficient_data` unaffected when returned early).

### Scope adherence

- Used exact test and implementation blocks from brief.
- No UI or repository changes.

### Concerns / notes for Task 5+

- When all five rules fire, cap drops `on_track_streak` and `logging_gap` — by design per rank priority.
- `selectHomeCallout` uses input list order for ACTIONABLE tie-breaking; ranking in `evaluate` does not reorder the list passed to callout selection unless caller uses ranked output.

---

## Files Changed

| File | Change |
|------|--------|
| `ProgressInsightEngine.kt` | +11 lines — rank sort before `take(MAX)` |
| `ProgressInsightEngineTest.kt` | +66 lines — 4 tests |

---

## Verification Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
```

**Result:** PASS (12/12)

---

## Status

**DONE** — All brief requirements met, tests green, commit on `feature/homeCallouts`.
