# Task 3 Report — Rules: `weekend_calorie_spike` + `plateau_under_target`

**Branch:** `feature/homeCallouts`  
**Base:** `cb4c8d5` (Task 2 — logging_gap, protein_shortfall, on_track_streak)  
**Commit:** `08cba21` — feat(insights): add weekend spike and plateau rules  
**Date:** 2026-07-22

---

## Summary

Extended `ProgressInsightEngine.evaluate` with two detection rules after existing Task 2 rules:

| Rule ID | Trigger | Severity | Action |
|---------|---------|----------|--------|
| `weekend_calorie_spike` | Sat–Sun avg calories ≥ 115% of weekday avg in 7-day window | ACTIONABLE | OpenProgress |
| `plateau_under_target` | ≥3 of last 5 logged days under calorie target **and** weight delta ≤ 0.4 kg over 14-day lookback (≥2 weight points) | ACTIONABLE | OpenWeightLog |

Results remain capped at `MAX_PROGRESS_INSIGHTS` (3). No ranking sort added (Task 4).

---

## TDD Workflow

### Step 1 — Failing tests appended

Added to `ProgressInsightEngineTest.kt`:

- `import java.time.DayOfWeek`
- `weekendSpike_whenWeekendAvgAtLeast15PercentAboveWeekday` — Thu–Wed window with Sat/Sun at 2500 kcal, weekdays at 1800 kcal
- `plateau_whenUnderTargetOftenAndWeightFlat` — 7 days at 1500 kcal (under 2000 target), weights 80.0 → 80.2 kg (Δ 0.2)
- `plateau_skippedWhenFewerThanTwoWeights` — same food data, empty weights → plateau suppressed

### Step 2 — RED

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
BUILD FAILED — 8 tests, 2 failures (weekendSpike, plateau_whenUnderTargetOftenAndWeightFlat)
```

### Step 3 — Implementation

Added to `ProgressInsightEngine.evaluate` before `return`:

1. **weekend_calorie_spike:** split `foodsInWindow` into weekend/weekday buckets; compare averages; fire when `weekendAvg >= weekdayAvg * WEEKEND_SPIKE_RATIO` (1.15)
2. **plateau_under_target:** sample last 5 logged days from 14-day lookback; count under calorie target; require ≥2 weight points in 14-day window with abs delta ≤ 0.4 kg

Removed unused `LocalDate` import (now only `DayOfWeek` and `abs` from Task 1 placeholders are used).

### Step 4 — GREEN

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
BUILD SUCCESSFUL — 8 tests, 0 failures
```

### Step 5 — Commit

Only Task 3 files staged and committed:

- `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt`
- `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt`

---

## Self-Review

### Correctness

- **weekend_calorie_spike:** Window Thu 16 – Wed 22 contains Sat/Sun (2500) and 5 weekdays (1800). Weekend avg 2500 ≥ 1800 × 1.15 = 2070 → rule fires. Requires both buckets non-empty.
- **plateau_under_target:** All 5 sampled days at 1500 < 2000 → underCount 5 ≥ 3. Two weights in 14-day window, delta 0.2 ≤ 0.4 → rule fires.
- **plateau gating:** Empty weights → `weightPoints.size < 2` → plateau suppressed; test passes via `!in ids`.
- **Overlap:** Weekend test also triggers `on_track_streak` (5 weekdays at exactly 1800 = lower bound of ±10% band). Tests use `contains` — correct per brief.

### Android-free

Engine uses only `java.time.DayOfWeek` and Kotlin stdlib (`kotlin.math.abs`). No Android imports.

### Scope adherence

- Did not implement ranking sort (Task 4).
- Used exact test code and implementation blocks from brief.
- `take(MAX_PROGRESS_INSIGHTS)` unchanged; insertion order until Task 4.

### Concerns / notes for Task 4

- With 5 rules active, dense scenarios may hit the 3-insight cap before higher-priority rules appear — Task 4 ranking sort will address ordering.
- Plateau uses `recentLogged` (14-day lookback, sorted descending) shared with protein_shortfall — consistent with design spec.

---

## Files Changed

| File | Change |
|------|--------|
| `ProgressInsightEngine.kt` | +44 lines — weekend spike + plateau blocks; removed unused `LocalDate` import |
| `ProgressInsightEngineTest.kt` | +38 lines — 3 tests + `DayOfWeek` import |

---

## Verification Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
```

**Result:** PASS (8/8)

---

## Status

**DONE** — All brief requirements met, tests green, commit on `feature/homeCallouts`.
