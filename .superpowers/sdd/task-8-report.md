# Task 8 Report — Wire Home callout + navigate to Progress

**Branch:** `feature/homeCallouts`  
**Base:** `103bf3d` (Task 7 — Progress tab insights)  
**Commit:** `9db1fc2` — feat(insights): add dismissible Home callout linked to Progress  
**Date:** 2026-07-22

---

## Summary

Added a dismissible Home insight callout that surfaces the top actionable (or logging-gap fallback) insight via `ProgressInsightEngine.selectHomeCallout`. Tapping the card navigates to the Progress tab; dismiss persists through `InsightPreferences`.

---

## Implementation

### Step 1 — Extend models

- `HomeUiState.activeCallout: ProgressInsight?`
- `HomeNavEvent.OpenProgressInsights`

### Step 2 — HomeViewModel

- Injected `InsightPreferences`.
- Combined `insightPreferences.dismissedIds` into `uiState` so dismiss re-evaluates callout.
- `buildInsights` mirrors `WeightTrackingViewModel` (all foods/weights → `InsightEngineInput` → `ProgressInsightEngine.evaluate`).
- `activeCallout` null when no goal or `dailyGoal <= 0`; else `selectHomeCallout(insights, dismissedIds)`.
- `dismissCallout()` / `onCalloutClicked()` as specified.

### Step 3 — Layout

- `cardInsightCallout` inserted in `fragment_home.xml` after date header, before calorie card.

### Step 4 — HomeFragment

- `bindCalloutCard` uses `ProgressInsightUiMapper` for title/body (with `formatArgs`).
- Card click → `onCalloutClicked`; dismiss button → `dismissCallout`.
- `OpenProgressInsights` → `MainActivity.openProgressTab()`.

### Step 5 — MainActivity

- Public `openProgressTab()` pops camera back stack and `showTab(TAG_PROGRESS, updateBottomNav = true)`.

### Step 6 — Compile

`.\gradlew.bat :app:compileDebugKotlin` — BUILD SUCCESSFUL.

### Step 7 — Commit

Staged and committed only the five Task 8 files.

---

## Verification

| Check | Result |
|-------|--------|
| `activeCallout` in `HomeUiState` | Done |
| `InsightPreferences` in combine | Done |
| Callout card layout + binding | Done |
| Navigate to Progress tab | Done |
| Compile | BUILD SUCCESSFUL |

---

## Concerns

- `buildInsights` is duplicated between `HomeViewModel` and `WeightTrackingViewModel`; could be extracted to a shared helper in a follow-up.
- Dismiss button click is isolated from card navigation (child consumes tap).
- No device/emulator UI test run in this task.
