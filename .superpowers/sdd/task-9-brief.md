### Task 9: Full unit suite + manual E2E checklist

**Files:**
- None new (verification only); optionally refresh dismissals when week rolls by ensuring `InsightPreferences` re-reads week on each `dismissedIds` access â€” already handled in `readDismissedForCurrentWeek` on init; add `fun refreshWeekIfNeeded()` called from HomeViewModel init / each evaluate if needed:

If `dismissedIds` can go stale across midnight week boundary while process lives, call from HomeViewModel when building state:

```kotlin
insightPreferences.ensureCurrentWeek()
```

Implement `ensureCurrentWeek()` as public wrapper around `readDismissedForCurrentWeek()` updating `_dismissedIds`.

**Interfaces:**
- Consumes: all prior tasks
- Produces: verified green unit suite + manual checklist passed by human

- [ ] **Step 1: Add `ensureCurrentWeek()` if missing and call from Home/Progress evaluate paths**

- [ ] **Step 2: Run full insight unit tests**

Run:

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.insights.*"
```

Expected: PASS

- [ ] **Step 3: Manual E2E** (human / device)

1. Seed â‰¥5 days food under target + 2 flat weights â†’ Progress shows plateau; Home shows callout  
2. Tap callout â†’ Progress tab opens  
3. Dismiss Home callout â†’ kill app â†’ reopen â†’ still dismissed  
4. Change device date into next ISO week â†’ dismissals cleared (banner can return)

- [ ] **Step 4: Commit any `ensureCurrentWeek` fix**

```bash
git add app/src/main/java/com/example/calories/data/preferences/InsightPreferences.kt app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt
git commit -m "fix(insights): refresh dismissals when ISO week changes"
```

(Skip commit if no code change.)

---

## Spec coverage self-check

| Spec requirement | Task |
|------------------|------|
| Progress Weekly Insights 1â€“3 cards | 7 |
| Home single dismissible callout | 8 |
| Pure `ProgressInsightEngine` | 1â€“4 |
| Rule catalog + ranking | 2â€“4 |
| Weight gating only on plateau | 3 |
| Insufficient data suppresses others | 1 |
| SharedPreferences dismiss + ISO week | 5 |
| EN + VI strings | 6 |
| No goals â†’ hide | 7â€“8 (`hasGoals` / null callout) |
| Unit tests for engine | 1â€“4, 9 |
| Manual E2E | 9 |
| Out of scope (Fridge, adaptive goals, AI copy) | Not planned |

## Type consistency notes

- Insight id strings: only `ProgressInsightIds.*`
- Engine input: `InsightEngineInput` / `InsightFoodDay` / `InsightWeightPoint`
- Home selection: `ProgressInsightEngine.selectHomeCallout`
- UI strings: `ProgressInsightUiMapper.titleRes` / `bodyRes`
- Prefs: `InsightPreferences.PREFS_NAME = "insight_prefs"`
