# Task 6 Report — Strings (EN + VI) + `ProgressInsightUiMapper`

**Branch:** `feature/homeCallouts`  
**Base:** `ffd296a` (Task 5 — ISO week keys + dismiss preferences)  
**Commit:** `7e92423` — feat(insights): add EN/VI copy and UI string mapper  
**Date:** 2026-07-22

---

## Summary

Added 15 insight string resources in English and Vietnamese (parity keys) and `ProgressInsightUiMapper` mapping each `ProgressInsightIds` constant to `@StringRes` title/body ids, with fallbacks for unknown ids.

---

## Implementation

### Step 1 — English strings

Appended 15 keys near the Progress section in `values/strings.xml`: section title, six insight title/body pairs, dismiss label, and home callout content description.

### Step 2 — Vietnamese parity

Same keys in `values-vi/strings.xml` under Progress / Analytics, using UTF-8 Vietnamese copy from the plan spec.

### Step 3 — Mapper

`ProgressInsightUiMapper.kt` — `titleRes(id)` and `bodyRes(id)` `when` branches for all six insight ids; `else` → section title / insufficient body.

### Step 4 — Commit

Staged and committed only Task 6 files (3 files, +58 lines).

---

## Self-Review

### Correctness

- All six `ProgressInsightIds` map to distinct title/body pairs.
- `%1$s` placeholders preserved on `logging_gap` and `on_track_streak` bodies.
- VI keys match EN key set exactly.

### Scope adherence

- No UI wiring, tests, or `formatArgs` formatting — mapper returns resource ids only per brief.

### Concerns / notes for Task 7+

- Callers must apply `formatArgs` via `getString(bodyRes(id), *args)` for templated bodies.
- Unknown ids fall back to generic section title / insufficient body — intentional per brief.

---

## Files Changed

| File | Change |
|------|--------|
| `values/strings.xml` | +15 insight string resources |
| `values-vi/strings.xml` | +15 VI parity strings |
| `ProgressInsightUiMapper.kt` | New — id → `@StringRes` mapper |

---

## Verification Command

```powershell
.\gradlew.bat :app:compileDebugKotlin --quiet
```

**Result:** PASS (compile succeeds; no new unit tests per brief)

---

## Status

**DONE** — All brief requirements met, compile green, commit on `feature/homeCallouts`.
