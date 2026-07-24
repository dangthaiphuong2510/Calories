# Hybrid Smart Meal Recommendation — Design Spec

**Date:** 2026-07-24  
**Status:** Approved for planning  
**Scope bet:** Client hybrid orchestrator — Supabase recipe search first, Gemini top-up when fewer than N matches, shared-catalog insert; Home CTA → Explore For you

## Goal

Help goal-chasers pick a meal that closes today’s **biggest remaining macro gap**, using the existing recipe catalog first and Gemini only to fill a short list — then saving generated recipes into the shared Supabase catalog so the DB grows automatically.

**Positioning:** Calories suggests what to eat for *what’s left today*, not just another recipe browser.

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Surfaces | Home CTA + Explore **For you** (same engine) |
| Matching | Prioritize **dominant remaining macro gap**; kcal as guardrail |
| Gemini trigger | If ranked DB matches `< N` (default **N=3**), generate `N − count` |
| Ownership | **Shared public catalog** inserts |
| Fridge | **Out of scope** for v1 |
| Architecture | **Client hybrid orchestrator** (Approach 1) |

## Context (existing strengths reused)

- Home already exposes remaining calories and macro progress (`HomeUiState`, `CalorieCalculator.macroTargetsFor` 30/40/30).
- Explore already lists recipes with kcal server filters and client macro tags (`RecipeRepository`, `ExploreViewModel`).
- Recipe graph: `recipes`, `recipe_macros`, `recipe_ingredients`, `recipe_steps` (DTOs in `RecipeModels.kt`).
- Gemini today is image food analysis only (`GeminiAnalysisService`); same API key / error types can be reused for text JSON generation.
- Recipe detail already supports portion scale, add-to-meal, and rewarded unlock for steps.

## Product / UX

### Home — meal recommend CTA

- Show when `remainingKcal >= 150` and user is signed in.
- Copy reflects `dominantGap` (e.g. protein-focused vs calories).
- Tap → `MainActivity.openExploreForYou()` (Explore tab + For you mode).
- Does **not** replace the existing Progress insight callout; if both would show, prefer a compact secondary CTA (e.g. chip/button under macros) so insight callout stays primary — or show meal CTA only when no insight callout is active. **v1 rule:** show meal CTA when remaining kcal ≥ 150; if `activeCallout != null`, place meal CTA below the callout (both visible).

### Explore — For you mode

- Dedicated mode alongside existing search + filter chips (new chip or toggle: **For you**).
- Shows up to **3** ranked recipes with source badge: catalog vs AI.
- DB results appear first; missing slots show loading (“Generating a recipe…”).
- Opening a recipe uses existing `RecipeDetailFragment` / flow.
- Near-goal empty state when remaining kcal &lt; 150: no Gemini.

### Out of scope (v1)

- Fridge ingredient matching
- Auto-logging the recommended meal
- Generating recipe images (`image_url` null / placeholder)
- Edge Function orchestration
- Client UPDATE/DELETE on shared recipes
- Changing daily calorie/macro goals

## Architecture

```text
HomeViewModel                    ExploreViewModel (For you)
       │                                  │
       └──────────► MealRecommendFacade ◄─┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
 RemainingMacros   RecipeRepository   GeminiRecipeService
   Calculator      (search + insert)  (JSON → RecipeDraft)
         │               │
         │               ▼
         │         Supabase recipe tables
         ▼
   MealRecommendScorer (pure Kotlin)
```

### Units

| Unit | Responsibility | Android-free? |
|------|----------------|---------------|
| `RemainingMacrosCalculator` | Remaining kcal/P/C/F + `dominantGap` | Yes |
| `MealRecommendScorer` | Hard filters + ranking | Yes |
| `RecipeDraft` / parser | Gemini JSON → insertable draft | Yes (parser) |
| `GeminiRecipeService` | Call Gemini, validate, retry once | No (network) |
| `RecipeRepository` | Candidate search + shared insert + name dedupe | No |
| `MealRecommendFacade` | Orchestrate search → score → top-up → insert | Thin; testable with fakes |

### Domain shapes

```kotlin
enum class MacroGap { PROTEIN, CARBS, FAT, CALORIES }

data class RemainingMacros(
    val remainingKcal: Int,
    val remainingProteinG: Double,
    val remainingCarbsG: Double,
    val remainingFatG: Double,
    val dominantGap: MacroGap,
)

enum class RecommendSource { CATALOG, AI }

data class RecommendedRecipe(
    val recipe: Recipe,
    val source: RecommendSource,
    val score: Double,
)

data class RecipeDraft(
    val name: String,
    val totalKcal: Double,
    val difficulty: String?,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val ingredients: List<RecipeDraftIngredient>,
    val steps: List<RecipeDraftStep>,
)
```

Nutrition on recipes remains **per 100g base** so existing detail scaling stays correct.

## Data flow & matching

### Inputs

- `remainingKcal = max(0, dailyGoal − totalEaten + totalBurned)`
- Macro remainings = `max(0, target − current)` using `CalorieCalculator.macroTargetsFor(dailyGoal)`
- `dominantGap` = largest remaining macro grams; tie-break protein → carbs → fat; if all macro remainings are negligible vs targets, use `CALORIES`

### Supabase search

1. Kcal band: `0.5×–1.1× remainingKcal` (clamped to sensible bounds).
2. Fetch candidate pool (up to 40) with macros + ingredients.
3. Client scorer ranks; keep top that pass hard filters.

### Hard filters

- Discard if `totalKcal > remainingKcal * 1.1`
- If `remainingKcal < 150`: no recommendations / no Gemini

### Scoring

- Primary: how well recipe fills `dominantGap` (prefer ~70–100% of that gap)
- Secondary: soft penalty for blowing other macros
- Tertiary: closer `totalKcal` to remaining is slightly better

### Gemini top-up

- If ranked list size `< N` (3): generate `N − size` recipes
- Prompt: remaining targets + dominant gap + per-100g base + language (EN/VI) + JSON schema
- Validate: required fields, non-negative macros, ≥1 ingredient, ≥1 step; one automatic regenerate on schema failure
- Insert into shared tables; on insert failure prefer in-session display of draft only if we can still open detail without an id — **v1:** require successful insert for AI items (detail needs numeric id); on insert fail, omit slot + allow Retry
- Dedupe: if normalized name exists, return existing recipe instead of inserting
- Client rate limit: one Gemini top-up burst per For-you session (or short cooldown)

## Persistence / schema

Migration adds optional catalog metadata:

- `recipes.source` text (`catalog` | `ai`), default `catalog`
- `recipes.created_by` uuid nullable (auth uid)

RLS:

- Keep existing read behavior for recipes
- Allow **authenticated INSERT** on `recipes`, `recipe_macros`, `recipe_ingredients`, `recipe_steps`
- No client UPDATE/DELETE on shared recipes in v1

## Error handling

| Failure | Behavior |
|---------|----------|
| Offline / search fails | Error UI + retry; no Gemini |
| 0 DB matches, Gemini OK | AI-only list up to N |
| Partial Gemini | Keep successes |
| Gemini timeout/API | Keep DB results; snackbar + Retry |
| Invalid JSON | One regenerate; then drop slot |
| Insert fails | Omit AI slot + Retry |
| Signed out | Hide Home CTA; For you requires login for Gemini/insert (search may still work if anon read allowed) |

Reuse `GeminiApiException` → existing UI mapping where applicable.

## Testing

- Unit: `RemainingMacrosCalculator`, `MealRecommendScorer`
- Unit: Gemini draft JSON parse/validate (fixtures)
- Unit: `MealRecommendFacade` with fake repo/Gemini (DB≥N → no Gemini; DB&lt;N → generate count; Gemini fail → DB-only)
- Manual: Home CTA → Explore For you → open detail → add to meal

## Success criteria

1. Home shows meal CTA when remaining kcal ≥ 150.
2. For you returns ≤3 recipes ranked by dominant-gap scoring.
3. When DB yields &lt;3 good matches, Gemini fills the gap and successful inserts appear in the shared catalog for later users.
4. Fridge unused; no goal auto-changes; EN + VI strings present.
