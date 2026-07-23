# Task 2 Report — Rules: logging_gap, protein_shortfall, on_track_streak

**Branch:** `feature/homeCallouts`  
**Base:** `abe1104` (Task 1 — engine skeleton + insufficient_data)  
**Commit:** `cb4c8d5` — feat(insights): add logging gap, protein shortfall, on-track rules  
**Date:** 2026-07-22

---

## Summary

Extended `ProgressInsightEngine.evaluate` with three detection rules after the insufficient-data gate:

| Rule ID | Trigger | Severity | Action |
|---------|---------|----------|--------|
| `logging_gap` | ≥2 missing days in 7-day window | INFO | OpenProgress |
| `protein_shortfall` | ≥4 of last 5 logged days below 80% protein target | ACTIONABLE | OpenProgress |
| `on_track_streak` | ≥5 days in window within ±10% of calorie target | POSITIVE | none |

Results are capped at `MAX_PROGRESS_INSIGHTS` (3). No ranking sort yet (Task 4).

---

## TDD Workflow

### Step 1 — Failing tests appended

Added to `ProgressInsightEngineTest.kt`:

- `baseInput()` / `sevenDays()` helpers
- `loggingGap_whenTwoOrMoreDaysMissingInWindow` — 5 of 7 days logged → 2 missing
- `proteinShortfall_whenFourOfLastFiveLoggedDaysBelow80Percent` — 7 days at 100g vs 150g target
- `onTrackStreak_whenFiveDaysWithinTenPercentOfTarget` — 7 consecutive days at 2000 kcal target

### Step 2 — RED (not captured separately)

Tests were written per brief; implementation followed immediately. Prior engine returned `emptyList()` after sufficient-data gate, so new assertions would fail on empty results.

### Step 3 — Implementation

Replaced trailing `return emptyList()` in `ProgressInsightEngine.evaluate` with:

1. **logging_gap:** `missingDays = 7 - foodDayCount`; fires when `missingDays >= 2`
2. **protein_shortfall:** 14-day lookback, most recent 5 logged days, count below 80% protein target; fires when count ≥ 4
3. **on_track_streak:** count days in 7-day window within ±10% of calorie target; fires when count ≥ 5

### Step 4 — GREEN

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
BUILD SUCCESSFUL — 5 tests, 0 failures
```

### Step 5 — Commit

Only Task 2 files staged and committed:

- `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

---

## Self-Review

### Correctness

- **logging_gap:** Uses `foodsInWindow` day count (already deduplicated/aggregated by date). Test with offsets `[0,1,2,4,5]` yields 5 distinct days → missing = 2 → rule fires. `INSUFFICIENT_DATA` correctly excluded (5 ≥ MIN_FOOD_LOG_DAYS of 3).
- **protein_shortfall:** Takes 5 most recent logged days from 14-day lookback. Test data has 7 consecutive days all at 66% of target → all 5 in sample are short → rule fires.
- **on_track_streak:** All 7 days at exactly 2000 kcal with 2000 target → all within ±10% → count 7 ≥ 5 → rule fires.
- **Overlap:** Full 7-day tests may emit multiple insights (e.g. protein shortfall + on_track). Tests use `contains`, not exact list — correct per brief.

### Android-free

Engine uses only `java.time` and Kotlin stdlib. No Android imports added.

### Scope adherence

- Did not implement weekend spike, plateau (Task 3), or ranking sort (Task 4).
- `take(MAX_PROGRESS_INSIGHTS)` applied as specified; order is insertion order until Task 4.

### Pre-existing notes (unchanged)

- Unused imports in `ProgressInsightEngine.kt` (`DayOfWeek`, `LocalDate`, `abs`) remain from Task 1 skeleton — likely placeholders for Task 3. Not removed to keep diff minimal.

### Minor observations

- `proteinShortfall` test also triggers `on_track_streak` (2000 kcal on target) — harmless given `contains` assertions.
- No negative-path tests yet (e.g. 1 missing day should not fire logging_gap) — acceptable; Task brief only specified positive cases.

---

## Files Changed

| File | Change |
|------|--------|
| `ProgressInsightEngine.kt` | +52 lines — three rule blocks + `take(3)` |
| `ProgressInsightEngineTest.kt` | +50 lines — helpers + 3 tests |

---

## Verification Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
```

**Result:** PASS (5/5)

---

## Status

**DONE** — All brief requirements met, tests green, commit on `feature/homeCallouts`.
