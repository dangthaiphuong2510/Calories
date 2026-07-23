# Task 1 Report: Insight models, thresholds, engine skeleton + insufficient_data

## Status

**DONE**

## What Was Implemented

Pure-Kotlin Progress Insights foundation under `com.example.calories.insights`:

1. **`ProgressInsight.kt`** — Insight ID constants (`ProgressInsightIds`), severity enum, action sealed class, and input/output DTOs (`ProgressInsight`, `InsightFoodDay`, `InsightWeightPoint`, `InsightEngineInput`).

2. **`ProgressInsightThresholds.kt`** — All threshold constants for current and future rules (window size, minimum food log days, plateau/protein/on-track parameters, max insights cap).

3. **`ProgressInsightEngine.kt`** — Engine skeleton with:
   - `evaluate()`: filters/aggregates food days within the 7-day window; returns `insufficient_data` when fewer than 3 distinct logged days; otherwise returns empty list (rules added in later tasks).
   - `selectHomeCallout()`: stub for home callout selection (actionable first, then logging gap).

4. **`ProgressInsightEngineTest.kt`** — Two unit tests covering insufficient-data paths.

No Android dependencies (`R`, `Context`, AndroidX) in main source.

## TDD Evidence

### RED (Step 2)

**Command:**
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
```

**Result:** `BUILD FAILED` — compilation errors as expected:
- `Unresolved reference 'ProgressInsightEngine'`
- `Unresolved reference 'InsightEngineInput'`
- `Unresolved reference 'ProgressInsightIds'`
- (and related unresolved DTO references)

Exit code: 1

### GREEN (Step 4)

**Command:**
```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.ProgressInsightEngineTest"
```

**Result:** `BUILD SUCCESSFUL` — 2 tests passed (`emptyFoods_returnsOnlyInsufficientData`, `fewerThanThreeFoodDays_returnsOnlyInsufficientData`).

Exit code: 0

## Files Changed

| File | Action |
|------|--------|
| `app/src/main/java/com/example/calories/insights/ProgressInsight.kt` | Created |
| `app/src/main/java/com/example/calories/insights/ProgressInsightThresholds.kt` | Created |
| `app/src/main/java/com/example/calories/insights/ProgressInsightEngine.kt` | Created |
| `app/src/test/java/com/example/calories/insights/ProgressInsightEngineTest.kt` | Created |

## Commit

```
abe1104 feat(insights): add engine skeleton with insufficient_data rule
```

Only the four Task 1 files were staged and committed.

## Self-Review

**Matches spec:** Implementation follows the task brief verbatim — models, thresholds, engine skeleton, and tests match the provided code.

**Correctness:**
- Empty food list → single `insufficient_data` insight with `INFO` severity.
- Two food days in window → same insufficient-data result.
- Food days outside the 7-day window are excluded before counting (implicit via window filter; not yet tested).
- `dailyCalorieTarget <= 0` returns empty list (guard for invalid input; not tested in Task 1).

**Android-free:** Verified — only `java.time` and `kotlin.math` imports in engine; no platform deps.

**Minor notes (not blockers):**
- `ProgressInsightEngine.kt` imports `DayOfWeek` and `abs` unused — intentional skeleton per brief for upcoming rules.
- Test file imports `assertTrue` unused — copied verbatim from brief.
- `selectHomeCallout()` has no tests yet — deferred to later tasks per plan.
- When food days ≥ 3, engine returns empty list until Task 2+ rules are added — expected skeleton behavior.

## Concerns

None blocking. Future tasks should add tests for window boundary behavior, `dailyCalorieTarget <= 0`, and `selectHomeCallout()` once wired to UI.
