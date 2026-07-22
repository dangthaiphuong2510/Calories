# Progress Insights + Home Callouts — Design Spec

**Date:** 2026-07-22  
**Status:** Approved for planning  
**Scope bet:** Small — 2 surfaces, reuse existing logs/goals; no Fridge, adaptive targets, or AI coaching prose

## Goal

Differentiate Calories for **weight-loss / fitness goal-chasers** by explaining *why* progress stalls or succeeds, using the user’s own food, weight, exercise, and goal data — not by adding another food database or scan gimmick.

**Positioning:** Calories helps you understand why the scale isn’t moving, not just log calories.

## Context (existing strengths reused)

- Progress tab already charts weight, calorie trend, and macro distribution (`WeightTrackingFragment` / `WeightTrackingViewModel`).
- Home already shows daily targets and intake-threshold warnings (`HomeViewModel`, notification prefs).
- Offline-first Room data: `food_entries`, `weight_entries`, `exercise_entries`, `user_goals` (+ water available but not required for v1 rules).

## Product / UX

### Progress tab — Weekly Insights (primary)

- Place an insights section **above** existing charts.
- Show **1–3** insight cards for a rolling **last 7 days** window.
- Each card: short title, one-sentence body, optional CTA (e.g. open weight log, jump to a related day, or stay on Progress).
- Low-data empty state: invite the user to log food and weight for a few days — **never invent advice**.

### Home — Callout banner (secondary)

- At most **one** dismissible banner when a high-signal (“actionable”) insight is active.
- Tap → navigate to Progress insights section.
- Dismiss → hide that insight id for the current week (Progress may still show it).

### Out of scope (this bet)

- Wiring Fridge or recipe matching
- Automatic calorie/macro goal changes
- Streaks, social, or accountability check-ins as a product line
- Gemini / free-form AI coaching copy (rules + string templates only)
- New Room tables or remote insight APIs

## Architecture

### `ProgressInsightEngine` (pure Kotlin)

- **No Android dependencies** — unit-testable like `AuthErrorMapper`.
- **Input:** last N days of food entries, exercise entries, weight entries, user goals (calorie + macro targets).
- **Output:** ranked `List<ProgressInsight>`.

Suggested shape (engine stays Android-free — **no `R.string` / resource ids**):

```kotlin
data class ProgressInsight(
    val id: String,           // stable rule id, e.g. "plateau_under_target"
    val severity: InsightSeverity, // ACTIONABLE | INFO | POSITIVE
    val formatArgs: List<String> = emptyList(), // preformatted numbers/labels for string templates
    val action: InsightAction? = null,
)

enum class InsightSeverity { ACTIONABLE, INFO, POSITIVE }

sealed class InsightAction {
    data object OpenProgress : InsightAction()
    data object OpenWeightLog : InsightAction()
}
```

UI/ViewModel maps `id` → `titleRes` / `bodyRes` via a small lookup table. That keeps the engine pure and unit-testable without Android resources.

### Call sites

| Surface | Owner | Behavior |
|---------|--------|----------|
| Progress | `WeightTrackingViewModel` | Run engine; expose `insights` (top 3) on UI state |
| Home | `HomeViewModel` | Run same engine; pick first non-dismissed **ACTIONABLE** insight; if none, may show `logging_gap` (INFO); never show POSITIVE on Home |

Shared engine instance/logic via a small domain helper or `@Inject` singleton with no framework deps beyond the pure function/object. **No duplicated rule logic** between Home and Progress.

### Persistence

- **DataStore** (app preferences or a tiny dedicated prefs): dismissed insight id(s) + **ISO week key** (e.g. `2026-W30`).
- New week → dismissals cleared.
- **No new Room entities**; read through existing repositories (`Food`, `Weight`, `Exercise`, `UserGoals`).

### UI / i18n

- Progress: insights list/section bound from `WeightTrackingUiState.insights`.
- Home: single Material banner/card from `HomeUiState.activeCallout` (nullable).
- All user-facing copy in `strings.xml` with **`values-vi` parity** for new strings.

## Insight rules (v1 catalog)

Window: **last 7 days** unless noted. Max **3** on Progress, **1** on Home.

| Id | Trigger | Intent | Home eligible |
|----|---------|--------|---------------|
| `plateau_under_target` | ≥3 of last 5 **logged** days under calorie target **and** weight ≈ flat over ~7–14 days | Under target but scale not moving — check portions / logging | Yes (ACTIONABLE) |
| `weekend_calorie_spike` | Sat–Sun average calories meaningfully above weekday average | Weekends driving surplus | Yes (ACTIONABLE) |
| `protein_shortfall` | Protein &lt; ~80% of target on ≥4 of last 5 logged days | Protein consistently low | Yes (ACTIONABLE) |
| `logging_gap` | ≥2 days with no food logs in last 7 | Missing logs weaken trust in progress | Optional (INFO); include on Home only if no ACTIONABLE insight |
| `on_track_streak` | ≥5 days within ±10% of calorie target | Positive adherence reinforcement | No (POSITIVE, Progress only) |
| `insufficient_data` | &lt;3 days with food logs in window | Soft empty state for the insights section; **suppress all other rules** | — |

**Weight gating:** `plateau_under_target` additionally requires ≥2 weight points in ~7–14 days; if food data is sufficient but weights are missing, suppress only that rule (still allow protein / weekend / logging / on-track).

**Ranking (highest first):** `plateau_under_target` → `weekend_calorie_spike` → `protein_shortfall` → `logging_gap` → `on_track_streak`.

**Guardrails**

- Never auto-edit goals.
- Observational copy only (“your logs show…”); no medical claims or prescriptions.
- Thresholds computed in metric internally; display via existing unit prefs where numbers appear in copy.
- Exact numeric thresholds (e.g. “flat” weight delta, “meaningfully above”) are fixed constants in the engine and covered by unit tests — tune in implementation, not at runtime.

## Error handling & edge cases

| Case | Behavior |
|------|----------|
| No user goals yet | Hide insights section and Home callout |
| Sparse data | Only `insufficient_data` / empty-state messaging |
| Repo sync failure | Insights use current Room snapshot; no dedicated insight error toast |
| Home dismiss | Persist id + week key; Progress still lists the insight that week |
| Conflicting rules | Engine ranks; UI takes top N — single Home banner |
| Missing VI strings | Required for release of this feature; add with EN |

## Testing

### Unit (required)

`ProgressInsightEngine` fixtures for:

- Each rule firing in isolation
- Ranking order when multiple fire
- `insufficient_data` suppressing others
- Home eligibility filter (ACTIONABLE vs POSITIVE)
- Edge: empty lists, single day, no weights

Follow existing JUnit style (`AuthErrorMapperTest`).

### Manual E2E

1. Seed ~7 days of food/weight so at least one ACTIONABLE rule fires.
2. Confirm Progress shows cards and Home shows banner.
3. Dismiss Home banner → kill/reopen → still dismissed.
4. Advance week (change device date into next ISO week, or a debug week-key override if added during implementation) → dismissals reset.

No new UI instrumentation suite in this bet.

## Success criteria

- Goal-chasers can open Progress and see a clear, personal reason tied to **their** recent logs.
- Home surfaces at most one high-signal nudge without replacing intake-threshold UI.
- Engine is fully covered by unit tests; no network dependency for insights.

## Non-goals reminder

Ship readiness polish (Fridge nav, orphaned Food Log, ads/auth WIP) and monetization (premium) are **out of scope** for this spec; they may appear in separate reviews.
