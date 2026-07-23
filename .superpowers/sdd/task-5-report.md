# Task 5 Report — ISO week keys + `InsightPreferences`

**Branch:** `feature/homeCallouts`  
**Base:** `2658622` (Task 4 — ranking order + home callout tests)  
**Commit:** `ffd296a` — feat(insights): add week keys and dismiss preferences  
**Date:** 2026-07-22

---

## Summary

Added `InsightWeekKeys.isoWeekKey` for ISO `YYYY-Www` formatting and `InsightPreferences` (Hilt `@Singleton`, SharedPreferences) to persist dismissed insight ids per ISO week. `LocalDataWiper.wipeAll()` now clears insight prefs after notification prefs.

---

## TDD Workflow

### Step 1 — Failing test

Created `InsightWeekKeysTest.isoWeekKey_formatsYearAndWeek` (2026-07-22 → `2026-W30`).

### Step 2 — RED

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"
BUILD FAILED — Unresolved reference 'InsightWeekKeys'
```

### Step 3 — Implementation

- `InsightWeekKeys.kt` — `WeekFields.ISO` week-based year + zero-padded week
- `InsightPreferences.kt` — `dismissedIds` StateFlow, `dismiss()`, `clear()`, week rollover clears stale prefs on read
- `LocalDataWiper.kt` — clears `InsightPreferences.PREFS_NAME` after notification prefs

### Step 4 — GREEN

```
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"
BUILD SUCCESSFUL — 1 test, 0 failures
```

### Step 5 — Commit

Staged and committed only Task 5 files (4 files, +90 lines).

---

## Self-Review

### Correctness

- ISO week key matches brief fixture (`2026-W30` for 2026-07-22).
- Dismissals scoped to current ISO week; stale week key triggers prefs clear on init/read.
- `LocalDataWiper` placement matches brief (after notification prefs, before exercise prefs).

### Scope adherence

- Followed `AppPreferences` / `NotificationPreferences` patterns (SharedPreferences + StateFlow).
- No Hilt module changes needed — `@Inject` constructor sufficient.

### Concerns / notes for Task 6+

- Brief interface lists `dismissedIdsForToday()` but implementation exposes `dismissedIds: StateFlow<Set<String>>` per plan Step 3 — Task 7 may add `refreshWeekIfNeeded()` for week rollover while app is running.
- No unit tests for `InsightPreferences` (Android/SharedPreferences); week-key logic covered in pure-Kotlin test.
- `LocalDataWiper` clears insight prefs directly via SharedPreferences rather than injecting `InsightPreferences` — consistent with notification prefs pattern.

---

## Files Changed

| File | Change |
|------|--------|
| `InsightWeekKeys.kt` | New — ISO week key formatter |
| `InsightPreferences.kt` | New — dismiss persistence |
| `InsightWeekKeysTest.kt` | New — week key unit test |
| `LocalDataWiper.kt` | +4 lines — clear insight prefs on wipe |

---

## Verification Command

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.InsightWeekKeysTest"
```

**Result:** PASS (1/1)

---

## Status

**DONE** — All brief requirements met, tests green, commit on `feature/homeCallouts`.
