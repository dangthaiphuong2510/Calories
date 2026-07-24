# Hybrid Smart Meal Recommendation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recommend up to 3 meals that close today’s dominant remaining macro gap via Supabase search first, Gemini top-up when fewer than 3 matches, shared-catalog insert, with Home CTA → Explore For you.

**Architecture:** Pure Kotlin `RemainingMacrosCalculator` + `MealRecommendScorer`; `MealRecommendFacade` orchestrates `RecipeRepository` candidate search/insert and `GeminiRecipeService`. Home shows a CTA when remaining kcal ≥ 150; Explore For you mode recomputes remaining macros and runs the facade.

**Tech Stack:** Kotlin, Hilt, Supabase PostgREST, Gemini (`gemini-3.5-flash` JSON), JUnit, View Binding, existing Explore/Home UI.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-07-24-hybrid-smart-meal-recommendation-design.md`
- Package root: `com.example.calories`
- `N = 3` recommendations max; Gemini only when ranked DB count `< 3`
- Hard kcal filter: discard if `totalKcal > remainingKcal * 1.1`
- Min remaining for feature: **150 kcal** (no Gemini / empty For you near goal)
- Macro targets: `CalorieCalculator.macroTargetsFor` (30% P / 40% C / 30% F) — do not invent new ratios
- Recipe nutrition stays **per 100g base** (existing detail scaling)
- Shared catalog inserts; no fridge; no recipe images generation
- All user-facing strings in `values/strings.xml` **and** `values-vi/strings.xml`
- Scorer / calculator / draft parser must stay Android-free (no `R`, Context, AndroidX)
- Run unit tests with: `.\gradlew.bat :app:testDebugUnitTest --tests "<fqcn>"`
- YAGNI: no Edge Functions, no client UPDATE/DELETE on recipes, no auto-log meal

---

## File structure

| File | Responsibility |
|------|----------------|
| `app/src/main/java/com/example/calories/recommend/MacroGap.kt` | `MacroGap`, `RemainingMacros`, `RecommendSource`, `RecommendedRecipe` |
| `app/src/main/java/com/example/calories/recommend/RemainingMacrosCalculator.kt` | Pure remaining + dominant gap |
| `app/src/main/java/com/example/calories/recommend/MealRecommendScorer.kt` | Hard filter + ranking |
| `app/src/main/java/com/example/calories/recommend/MealRecommendConstants.kt` | `N`, min kcal, band multipliers |
| `app/src/main/java/com/example/calories/recommend/RecipeDraft.kt` | Draft DTOs for Gemini → insert |
| `app/src/main/java/com/example/calories/recommend/RecipeDraftParser.kt` | JSON → `RecipeDraft` + validation |
| `app/src/main/java/com/example/calories/data/network/service/GeminiRecipeService.kt` | Gemini generate + one retry |
| `app/src/main/java/com/example/calories/recommend/MealRecommendFacade.kt` | Orchestrator |
| `app/src/test/java/com/example/calories/recommend/*Test.kt` | Unit tests |
| `supabase/migrations/20260724120000_recipe_ai_catalog.sql` | `source`, `created_by`, INSERT RLS |
| Modify: `RecipeRepository.kt` / `RecipeRepositoryImpl.kt` | `fetchRecommendCandidates`, `findRecipeByNormalizedName`, `insertRecipe` |
| Modify: `ExploreViewModel.kt` / `ExploreFragment.kt` / layouts | For you mode |
| Modify: `HomeViewModel.kt` / `HomeFragment.kt` / home layout | Meal CTA |
| Modify: `MainActivity.kt` | `openExploreForYou()` |
| Modify: `RecipeCardAdapter` / `item_recipe_card.xml` | Optional AI badge |
| Modify: `strings.xml`, `values-vi/strings.xml` | Copy |

---

### Task 1: Remaining macros calculator

**Files:**
- Create: `app/src/main/java/com/example/calories/recommend/MacroGap.kt`
- Create: `app/src/main/java/com/example/calories/recommend/MealRecommendConstants.kt`
- Create: `app/src/main/java/com/example/calories/recommend/RemainingMacrosCalculator.kt`
- Test: `app/src/test/java/com/example/calories/recommend/RemainingMacrosCalculatorTest.kt`

**Interfaces:**
- Consumes: none (pure math; callers pass eaten/burned/targets)
- Produces:
  - `enum class MacroGap { PROTEIN, CARBS, FAT, CALORIES }`
  - `data class RemainingMacros(remainingKcal: Int, remainingProteinG: Double, remainingCarbsG: Double, remainingFatG: Double, dominantGap: MacroGap)`
  - `object MealRecommendConstants { const val RESULT_COUNT = 3; const val MIN_REMAINING_KCAL = 150; const val KCAL_OVERSHOOT_FACTOR = 1.1; const val KCAL_BAND_LOW = 0.5; const val KCAL_BAND_HIGH = 1.1; const val CANDIDATE_LIMIT = 40 }`
  - `RemainingMacrosCalculator.compute(dailyGoalKcal: Int, eatenKcal: Int, burnedKcal: Int, eatenProteinG: Double, eatenCarbsG: Double, eatenFatG: Double, targetProteinG: Double, targetCarbsG: Double, targetFatG: Double): RemainingMacros`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.calories.recommend

import org.junit.Assert.assertEquals
import org.junit.Test

class RemainingMacrosCalculatorTest {

    @Test
    fun dominantGap_isLargestRemainingMacro_proteinWinsTies() {
        val result = RemainingMacrosCalculator.compute(
            dailyGoalKcal = 2000,
            eatenKcal = 1000,
            burnedKcal = 0,
            eatenProteinG = 20.0,
            eatenCarbsG = 50.0,
            eatenFatG = 30.0,
            targetProteinG = 150.0,
            targetCarbsG = 200.0,
            targetFatG = 67.0,
        )
        assertEquals(1000, result.remainingKcal)
        assertEquals(130.0, result.remainingProteinG, 0.01)
        assertEquals(MacroGap.PROTEIN, result.dominantGap)
    }

    @Test
    fun remainingKcal_neverNegative() {
        val result = RemainingMacrosCalculator.compute(
            dailyGoalKcal = 2000,
            eatenKcal = 2500,
            burnedKcal = 100,
            eatenProteinG = 200.0,
            eatenCarbsG = 300.0,
            eatenFatG = 100.0,
            targetProteinG = 150.0,
            targetCarbsG = 200.0,
            targetFatG = 67.0,
        )
        assertEquals(0, result.remainingKcal)
        assertEquals(0.0, result.remainingProteinG, 0.01)
        assertEquals(MacroGap.CALORIES, result.dominantGap)
    }

    @Test
    fun burnedCalories_increaseRemaining() {
        val result = RemainingMacrosCalculator.compute(
            dailyGoalKcal = 2000,
            eatenKcal = 1800,
            burnedKcal = 300,
            eatenProteinG = 100.0,
            eatenCarbsG = 180.0,
            eatenFatG = 60.0,
            targetProteinG = 150.0,
            targetCarbsG = 200.0,
            targetFatG = 67.0,
        )
        assertEquals(500, result.remainingKcal)
        assertEquals(MacroGap.PROTEIN, result.dominantGap)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.RemainingMacrosCalculatorTest"`

Expected: FAIL (classes not found)

- [ ] **Step 3: Write minimal implementation**

`MealRecommendConstants.kt`:

```kotlin
package com.example.calories.recommend

object MealRecommendConstants {
    const val RESULT_COUNT = 3
    const val MIN_REMAINING_KCAL = 150
    const val KCAL_OVERSHOOT_FACTOR = 1.1
    const val KCAL_BAND_LOW = 0.5
    const val KCAL_BAND_HIGH = 1.1
    const val CANDIDATE_LIMIT = 40L
    /** If every macro remaining is below this fraction of its target, treat gap as CALORIES. */
    const val NEGLIGIBLE_MACRO_FRACTION = 0.05
}
```

`MacroGap.kt`:

```kotlin
package com.example.calories.recommend

import com.example.calories.model.Recipe

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
```

`RemainingMacrosCalculator.kt`:

```kotlin
package com.example.calories.recommend

object RemainingMacrosCalculator {

    fun compute(
        dailyGoalKcal: Int,
        eatenKcal: Int,
        burnedKcal: Int,
        eatenProteinG: Double,
        eatenCarbsG: Double,
        eatenFatG: Double,
        targetProteinG: Double,
        targetCarbsG: Double,
        targetFatG: Double,
    ): RemainingMacros {
        val remainingKcal = (dailyGoalKcal - eatenKcal + burnedKcal).coerceAtLeast(0)
        val remainingProteinG = (targetProteinG - eatenProteinG).coerceAtLeast(0.0)
        val remainingCarbsG = (targetCarbsG - eatenCarbsG).coerceAtLeast(0.0)
        val remainingFatG = (targetFatG - eatenFatG).coerceAtLeast(0.0)

        val proteinNegligible = targetProteinG <= 0.0 ||
            remainingProteinG < targetProteinG * MealRecommendConstants.NEGLIGIBLE_MACRO_FRACTION
        val carbsNegligible = targetCarbsG <= 0.0 ||
            remainingCarbsG < targetCarbsG * MealRecommendConstants.NEGLIGIBLE_MACRO_FRACTION
        val fatNegligible = targetFatG <= 0.0 ||
            remainingFatG < targetFatG * MealRecommendConstants.NEGLIGIBLE_MACRO_FRACTION

        val dominantGap = if (proteinNegligible && carbsNegligible && fatNegligible) {
            MacroGap.CALORIES
        } else {
            // Tie-break: protein → carbs → fat
            when {
                remainingProteinG >= remainingCarbsG && remainingProteinG >= remainingFatG ->
                    MacroGap.PROTEIN
                remainingCarbsG >= remainingFatG -> MacroGap.CARBS
                else -> MacroGap.FAT
            }
        }

        return RemainingMacros(
            remainingKcal = remainingKcal,
            remainingProteinG = remainingProteinG,
            remainingCarbsG = remainingCarbsG,
            remainingFatG = remainingFatG,
            dominantGap = dominantGap,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.RemainingMacrosCalculatorTest"`

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/recommend/MacroGap.kt app/src/main/java/com/example/calories/recommend/MealRecommendConstants.kt app/src/main/java/com/example/calories/recommend/RemainingMacrosCalculator.kt app/src/test/java/com/example/calories/recommend/RemainingMacrosCalculatorTest.kt
git commit -m "feat(recommend): add remaining macros calculator"
```

---

### Task 2: Meal recommend scorer

**Files:**
- Create: `app/src/main/java/com/example/calories/recommend/MealRecommendScorer.kt`
- Test: `app/src/test/java/com/example/calories/recommend/MealRecommendScorerTest.kt`

**Interfaces:**
- Consumes: `RemainingMacros`, `MealRecommendConstants`, `Recipe` / `RecipeMacros`
- Produces:
  - `MealRecommendScorer.rank(candidates: List<Recipe>, remaining: RemainingMacros, limit: Int = RESULT_COUNT): List<Pair<Recipe, Double>>`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.calories.recommend

import com.example.calories.model.Recipe
import com.example.calories.model.RecipeMacros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRecommendScorerTest {

    private fun recipe(
        id: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
    ) = Recipe(
        id = id,
        name = id,
        imageUrl = null,
        totalKcal = kcal,
        difficulty = null,
        prepTimeMinutes = null,
        cookTimeMinutes = null,
        macros = RecipeMacros(carbsG = carbs, proteinG = protein, fatG = fat),
        ingredients = emptyList(),
        steps = emptyList(),
    )

    @Test
    fun discardsRecipesOverKcalCap() {
        val remaining = RemainingMacros(400, 40.0, 40.0, 20.0, MacroGap.PROTEIN)
        val ranked = MealRecommendScorer.rank(
            candidates = listOf(
                recipe("high", kcal = 500.0, protein = 40.0, carbs = 20.0, fat = 10.0),
                recipe("ok", kcal = 350.0, protein = 35.0, carbs = 20.0, fat = 10.0),
            ),
            remaining = remaining,
        )
        assertEquals(listOf("ok"), ranked.map { it.first.id })
    }

    @Test
    fun prefersHigherProteinWhenDominantGapIsProtein() {
        val remaining = RemainingMacros(500, 50.0, 60.0, 25.0, MacroGap.PROTEIN)
        val ranked = MealRecommendScorer.rank(
            candidates = listOf(
                recipe("low_p", kcal = 400.0, protein = 15.0, carbs = 40.0, fat = 15.0),
                recipe("high_p", kcal = 400.0, protein = 45.0, carbs = 30.0, fat = 12.0),
            ),
            remaining = remaining,
        )
        assertEquals("high_p", ranked.first().first.id)
        assertTrue(ranked.first().second > ranked.last().second)
    }

    @Test
    fun returnsAtMostLimit() {
        val remaining = RemainingMacros(600, 40.0, 50.0, 20.0, MacroGap.CARBS)
        val candidates = (1..10).map {
            recipe("r$it", kcal = 300.0, protein = 20.0, carbs = 40.0, fat = 10.0)
        }
        val ranked = MealRecommendScorer.rank(candidates, remaining, limit = 3)
        assertEquals(3, ranked.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.MealRecommendScorerTest"`

Expected: FAIL (Scorer not found)

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.calories.recommend

import com.example.calories.model.Recipe
import kotlin.math.abs
import kotlin.math.max

object MealRecommendScorer {

    fun rank(
        candidates: List<Recipe>,
        remaining: RemainingMacros,
        limit: Int = MealRecommendConstants.RESULT_COUNT,
    ): List<Pair<Recipe, Double>> {
        if (remaining.remainingKcal < MealRecommendConstants.MIN_REMAINING_KCAL) {
            return emptyList()
        }
        val maxKcal = remaining.remainingKcal * MealRecommendConstants.KCAL_OVERSHOOT_FACTOR
        return candidates
            .asSequence()
            .filter { it.totalKcal <= maxKcal }
            .map { it to score(it, remaining) }
            .sortedByDescending { it.second }
            .take(limit)
            .toList()
    }

    private fun score(recipe: Recipe, remaining: RemainingMacros): Double {
        val macros = recipe.macros
        val protein = macros?.proteinG ?: 0.0
        val carbs = macros?.carbsG ?: 0.0
        val fat = macros?.fatG ?: 0.0

        val gapFill = when (remaining.dominantGap) {
            MacroGap.PROTEIN -> fillScore(protein, remaining.remainingProteinG)
            MacroGap.CARBS -> fillScore(carbs, remaining.remainingCarbsG)
            MacroGap.FAT -> fillScore(fat, remaining.remainingFatG)
            MacroGap.CALORIES -> fillScore(recipe.totalKcal, remaining.remainingKcal.toDouble())
        }

        val otherPenalty = when (remaining.dominantGap) {
            MacroGap.PROTEIN -> overshootPenalty(carbs, remaining.remainingCarbsG) +
                overshootPenalty(fat, remaining.remainingFatG)
            MacroGap.CARBS -> overshootPenalty(protein, remaining.remainingProteinG) +
                overshootPenalty(fat, remaining.remainingFatG)
            MacroGap.FAT -> overshootPenalty(protein, remaining.remainingProteinG) +
                overshootPenalty(carbs, remaining.remainingCarbsG)
            MacroGap.CALORIES -> 0.0
        }

        val kcalCloseness = 1.0 - (
            abs(recipe.totalKcal - remaining.remainingKcal) /
                max(remaining.remainingKcal.toDouble(), 1.0)
            ).coerceIn(0.0, 1.0)

        return gapFill * 10.0 - otherPenalty + kcalCloseness
    }

    /** Prefer filling ~70–100% of the gap; overshoot past 100% decays. */
    private fun fillScore(amount: Double, gap: Double): Double {
        if (gap <= 0.0) return 0.0
        val ratio = amount / gap
        return when {
            ratio <= 0.0 -> 0.0
            ratio <= 1.0 -> ratio
            else -> (1.0 - (ratio - 1.0)).coerceAtLeast(0.0)
        }
    }

    private fun overshootPenalty(amount: Double, remaining: Double): Double {
        if (amount <= remaining) return 0.0
        return ((amount - remaining) / max(remaining, 1.0)).coerceAtMost(2.0)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.MealRecommendScorerTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/recommend/MealRecommendScorer.kt app/src/test/java/com/example/calories/recommend/MealRecommendScorerTest.kt
git commit -m "feat(recommend): add meal recommend scorer"
```

---

### Task 3: Recipe draft parser (Gemini JSON)

**Files:**
- Create: `app/src/main/java/com/example/calories/recommend/RecipeDraft.kt`
- Create: `app/src/main/java/com/example/calories/recommend/RecipeDraftParser.kt`
- Test: `app/src/test/java/com/example/calories/recommend/RecipeDraftParserTest.kt`

**Interfaces:**
- Consumes: JSON string from Gemini
- Produces:
  - `data class RecipeDraftIngredient(name: String, amount: Double, unit: String, kcal: Double)`
  - `data class RecipeDraftStep(stepNumber: Int, description: String)`
  - `data class RecipeDraft(...)` matching insert fields
  - `RecipeDraftParser.parse(json: String): RecipeDraft` (throws on invalid)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.example.calories.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeDraftParserTest {

    @Test
    fun parsesValidDraft() {
        val json = """
            {
              "name": "High Protein Bowl",
              "total_kcal": 420,
              "difficulty": "easy",
              "prep_time": 10,
              "cook_time": 15,
              "protein_g": 40,
              "carbs_g": 35,
              "fat_g": 12,
              "ingredients": [
                {"name": "Chicken", "amount": 150, "unit": "g", "kcal": 250}
              ],
              "steps": [
                {"step_number": 1, "description": "Cook chicken"}
              ]
            }
        """.trimIndent()
        val draft = RecipeDraftParser.parse(json)
        assertEquals("High Protein Bowl", draft.name)
        assertEquals(420.0, draft.totalKcal, 0.01)
        assertEquals(40.0, draft.proteinG, 0.01)
        assertEquals(1, draft.ingredients.size)
        assertEquals(1, draft.steps.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyIngredients() {
        val json = """
            {
              "name": "Bad",
              "total_kcal": 100,
              "protein_g": 10,
              "carbs_g": 10,
              "fat_g": 5,
              "ingredients": [],
              "steps": [{"step_number": 1, "description": "x"}]
            }
        """.trimIndent()
        RecipeDraftParser.parse(json)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankName() {
        RecipeDraftParser.parse(
            """{"name":" ","total_kcal":100,"protein_g":1,"carbs_g":1,"fat_g":1,
               "ingredients":[{"name":"a","amount":1,"unit":"g","kcal":1}],
               "steps":[{"step_number":1,"description":"d"}]}""",
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.RecipeDraftParserTest"`

Expected: FAIL

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.example.calories.recommend

data class RecipeDraftIngredient(
    val name: String,
    val amount: Double,
    val unit: String,
    val kcal: Double,
)

data class RecipeDraftStep(
    val stepNumber: Int,
    val description: String,
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

```kotlin
package com.example.calories.recommend

import org.json.JSONObject

object RecipeDraftParser {

    fun parse(json: String): RecipeDraft {
        val root = JSONObject(json.trim())
        val name = root.optString("name").trim()
        require(name.isNotEmpty()) { "name required" }

        val totalKcal = root.optDouble("total_kcal", Double.NaN)
        require(totalKcal.isFinite() && totalKcal >= 0.0) { "total_kcal invalid" }

        val proteinG = root.optDouble("protein_g", Double.NaN)
        val carbsG = root.optDouble("carbs_g", Double.NaN)
        val fatG = root.optDouble("fat_g", Double.NaN)
        require(proteinG.isFinite() && proteinG >= 0.0) { "protein_g invalid" }
        require(carbsG.isFinite() && carbsG >= 0.0) { "carbs_g invalid" }
        require(fatG.isFinite() && fatG >= 0.0) { "fat_g invalid" }

        val ingredientsJson = root.optJSONArray("ingredients")
            ?: throw IllegalArgumentException("ingredients required")
        require(ingredientsJson.length() >= 1) { "ingredients empty" }
        val ingredients = buildList {
            for (i in 0 until ingredientsJson.length()) {
                val item = ingredientsJson.getJSONObject(i)
                val iname = item.optString("name").trim()
                require(iname.isNotEmpty()) { "ingredient name required" }
                add(
                    RecipeDraftIngredient(
                        name = iname,
                        amount = item.optDouble("amount", 0.0).coerceAtLeast(0.0),
                        unit = item.optString("unit", "g"),
                        kcal = item.optDouble("kcal", 0.0).coerceAtLeast(0.0),
                    ),
                )
            }
        }

        val stepsJson = root.optJSONArray("steps")
            ?: throw IllegalArgumentException("steps required")
        require(stepsJson.length() >= 1) { "steps empty" }
        val steps = buildList {
            for (i in 0 until stepsJson.length()) {
                val item = stepsJson.getJSONObject(i)
                val description = item.optString("description").trim()
                require(description.isNotEmpty()) { "step description required" }
                add(
                    RecipeDraftStep(
                        stepNumber = item.optInt("step_number", i + 1),
                        description = description,
                    ),
                )
            }
        }

        return RecipeDraft(
            name = name,
            totalKcal = totalKcal,
            difficulty = root.optString("difficulty").ifBlank { null },
            prepTimeMinutes = root.optInt("prep_time").takeIf { root.has("prep_time") },
            cookTimeMinutes = root.optInt("cook_time").takeIf { root.has("cook_time") },
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            ingredients = ingredients,
            steps = steps.sortedBy { it.stepNumber },
        )
    }
}
```

Note: `org.json` is on the Android classpath and available to unit tests via Robolectric-free Android JAR in this project’s unit test setup (same as other JSON usage). If unit tests cannot resolve `org.json`, use `JSONObject` from the app’s existing approach or add `org.json:json` test dependency — prefer matching `GeminiAnalysisService` which already uses `org.json`.

- [ ] **Step 4: Run tests — PASS**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.RecipeDraftParserTest"`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/calories/recommend/RecipeDraft.kt app/src/main/java/com/example/calories/recommend/RecipeDraftParser.kt app/src/test/java/com/example/calories/recommend/RecipeDraftParserTest.kt
git commit -m "feat(recommend): add recipe draft JSON parser"
```

---

### Task 4: Supabase migration (source + INSERT RLS)

**Files:**
- Create: `supabase/migrations/20260724120000_recipe_ai_catalog.sql`

**Interfaces:**
- Consumes: existing `recipes` / child tables
- Produces: columns `source`, `created_by`; authenticated INSERT policies

- [ ] **Step 1: Write migration SQL**

```sql
-- AI / shared catalog metadata + authenticated insert for hybrid meal recommend
-- Apply via Supabase SQL editor or: supabase db push

alter table public.recipes
  add column if not exists source text not null default 'catalog',
  add column if not exists created_by uuid references auth.users (id) on delete set null;

alter table public.recipes
  drop constraint if exists recipes_source_check;

alter table public.recipes
  add constraint recipes_source_check check (source in ('catalog', 'ai'));

create index if not exists idx_recipes_name_lower on public.recipes (lower(name));

-- Enable RLS if not already (safe no-op when already enabled)
alter table public.recipes enable row level security;
alter table public.recipe_macros enable row level security;
alter table public.recipe_ingredients enable row level security;
alter table public.recipe_steps enable row level security;

-- Read: allow authenticated (and anon if you already expose catalog publicly — keep existing policies).
-- Only add INSERT for authenticated users. Do not add UPDATE/DELETE for clients.

drop policy if exists "recipes_insert_authenticated" on public.recipes;
create policy "recipes_insert_authenticated"
  on public.recipes for insert
  to authenticated
  with check (true);

drop policy if exists "recipe_macros_insert_authenticated" on public.recipe_macros;
create policy "recipe_macros_insert_authenticated"
  on public.recipe_macros for insert
  to authenticated
  with check (true);

drop policy if exists "recipe_ingredients_insert_authenticated" on public.recipe_ingredients;
create policy "recipe_ingredients_insert_authenticated"
  on public.recipe_ingredients for insert
  to authenticated
  with check (true);

drop policy if exists "recipe_steps_insert_authenticated" on public.recipe_steps;
create policy "recipe_steps_insert_authenticated"
  on public.recipe_steps for insert
  to authenticated
  with check (true);
```

**Important:** Before applying, inspect existing RLS on recipe tables in the Supabase dashboard. If SELECT policies are missing after enabling RLS, add a read policy matching current public catalog behavior, e.g. `create policy "recipes_select_all" on public.recipes for select using (true);` (and same for child tables). Do not lock out Explore list reads.

- [ ] **Step 2: Apply migration to the project Supabase** (SQL editor or CLI)

- [ ] **Step 3: Commit**

```bash
git add supabase/migrations/20260724120000_recipe_ai_catalog.sql
git commit -m "chore(db): add recipe AI source columns and insert RLS"
```

---

### Task 5: RecipeRepository candidates + insert + dedupe

**Files:**
- Modify: `app/src/main/java/com/example/calories/data/repository/RecipeRepository.kt`
- Modify: `app/src/main/java/com/example/calories/data/repository/RecipeRepositoryImpl.kt`
- Modify: `app/src/main/java/com/example/calories/model/RecipeModels.kt` (insert DTOs without id where needed)

**Interfaces:**
- Consumes: `RemainingMacros`, `RecipeDraft`, auth uid (optional for `created_by`)
- Produces:
  - `suspend fun fetchRecommendCandidates(minKcal: Double, maxKcal: Double, limit: Long = CANDIDATE_LIMIT): Result<List<Recipe>>`
  - `suspend fun findRecipeByNormalizedName(name: String): Result<Recipe?>`
  - `suspend fun insertSharedRecipe(draft: RecipeDraft, createdByUserId: String?): Result<Recipe>`

- [ ] **Step 1: Extend interface**

Add to `RecipeRepository`:

```kotlin
    suspend fun fetchRecommendCandidates(
        minKcal: Double,
        maxKcal: Double,
        limit: Long = 40L,
    ): Result<List<Recipe>>

    suspend fun findRecipeByNormalizedName(name: String): Result<Recipe?>

    suspend fun insertSharedRecipe(
        draft: com.example.calories.recommend.RecipeDraft,
        createdByUserId: String?,
    ): Result<Recipe>
```

- [ ] **Step 2: Implement candidate fetch**

In `RecipeRepositoryImpl`, query `recipes` with:

```kotlin
filter {
    gte("total_kcal", minKcal)
    lte("total_kcal", maxKcal)
}
limit(limit)
```

Use the same `recipeListColumns` embed as `fetchRecipes`. Order by `total_kcal` ascending.

- [ ] **Step 3: Implement name dedupe**

Normalize: `name.trim().lowercase()`. Fetch with `ilike("name", normalized)` or exact lower match; return first domain recipe or null.

- [ ] **Step 4: Implement insert**

Insert order:
1. `recipes` row: `name`, `image_url=null`, `total_kcal`, `difficulty`, `prep_time`, `cook_time`, `source='ai'`, `created_by`
2. Decode returned `id`
3. Insert `recipe_macros` (`recipe_id`, carbs/protein/fat)
4. Insert each `recipe_ingredients` (omit client `id` — use DB serial; define `@Serializable` insert DTO without id)
5. Insert each `recipe_steps`
6. `getRecipeById(id.toString())` and return

Use kotlinx.serialization insert DTOs, e.g.:

```kotlin
@Serializable
data class RecipeInsertDto(
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("total_kcal") val totalKcal: Double,
    val difficulty: String? = null,
    @SerialName("prep_time") val prepTime: Int? = null,
    @SerialName("cook_time") val cookTime: Int? = null,
    val source: String = "ai",
    @SerialName("created_by") val createdBy: String? = null,
)
```

Follow existing Supabase Kotlin insert patterns in this repo (e.g. fridge/food remote services). On failure, `Result.failure`.

- [ ] **Step 5: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/calories/data/repository/RecipeRepository.kt app/src/main/java/com/example/calories/data/repository/RecipeRepositoryImpl.kt app/src/main/java/com/example/calories/model/RecipeModels.kt
git commit -m "feat(recipes): add recommend candidates and shared AI insert"
```

---

### Task 6: GeminiRecipeService

**Files:**
- Create: `app/src/main/java/com/example/calories/data/network/service/GeminiRecipeService.kt`

**Interfaces:**
- Consumes: `RemainingMacros`, `AppLanguage` (or `Boolean isVietnamese`), `NetworkConnectivity`, `BuildConfig.GEMINI_API_KEY`
- Produces: `suspend fun generateRecipe(remaining: RemainingMacros, languageTag: String): RecipeDraft` (throws `GeminiApiException`)

- [ ] **Step 1: Implement service mirroring `GeminiAnalysisService`**

- Same host/model (`gemini-3.5-flash`), `responseMimeType=application/json`, `HttpTimeout` 15–30s (recipe gen may need 30s).
- Prompt must require: name, total_kcal, macros, ingredients[], steps[]; nutrition **per 100g base**; match remaining kcal band and fill `dominantGap`; language from `languageTag` (`en` / `vi`).
- `responseSchema` matching parser field names (`total_kcal`, `protein_g`, …).
- On parse failure: **one** regenerate call, then throw.
- Map network errors to `GeminiApiException` like analysis service.
- Extract JSON via same `extractModelText` / brace-repair approach — either duplicate private helpers or extract a small shared `GeminiJsonClient` helper in a follow-up only if duplication is painful; **v1 may duplicate** to stay YAGNI.

- [ ] **Step 2: Compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/data/network/service/GeminiRecipeService.kt
git commit -m "feat(recommend): add Gemini recipe generation service"
```

---

### Task 7: MealRecommendFacade (+ unit tests with fakes)

**Files:**
- Create: `app/src/main/java/com/example/calories/recommend/MealRecommendFacade.kt`
- Test: `app/src/test/java/com/example/calories/recommend/MealRecommendFacadeTest.kt`

**Interfaces:**
- Consumes: `RecipeRepository`, `GeminiRecipeService` (inject interfaces or constructor params for testability)
- Produces:
  - `suspend fun recommend(remaining: RemainingMacros, createdByUserId: String?, languageTag: String): Result<List<RecommendedRecipe>>`

Logic:
1. If `remaining.remainingKcal < MIN` → `Result.success(emptyList())`
2. `minKcal = remaining * KCAL_BAND_LOW`, `maxKcal = remaining * KCAL_BAND_HIGH`
3. `fetchRecommendCandidates` → on failure return that failure
4. `MealRecommendScorer.rank(...)` → `catalogHits`
5. `need = RESULT_COUNT - catalogHits.size`
6. If `need <= 0` → map catalog hits to `RecommendedRecipe(source=CATALOG)` and return
7. Else loop `need` times: `generateRecipe` → `findRecipeByNormalizedName` if exists use it as CATALOG (or AI if you prefer) → else `insertSharedRecipe` → append `RecommendedRecipe(source=AI)`; on single failure continue; never throw away catalog hits
8. Return combined list (catalog first, then AI), size ≤ 3

For tests, prefer constructor injection:

```kotlin
class MealRecommendFacade(
    private val recipeRepository: RecipeRepository,
    private val geminiRecipeService: GeminiRecipeService,
)
```

Hilt: `@Singleton class MealRecommendFacade @Inject constructor(...)`.

- [ ] **Step 1: Write failing facade tests with fakes**

Cover:
- DB returns ≥3 after scoring → Gemini never called
- DB returns 1 → Gemini called twice (or until need met)
- Gemini throws → still returns DB-only list as success
- remaining &lt; 150 → empty, no repo call

Use simple fake classes in the test file.

- [ ] **Step 2: Implement facade to pass tests**

- [ ] **Step 3: Run tests**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.MealRecommendFacadeTest"`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/calories/recommend/MealRecommendFacade.kt app/src/test/java/com/example/calories/recommend/MealRecommendFacadeTest.kt
git commit -m "feat(recommend): add meal recommend facade orchestration"
```

---

### Task 8: Explore For you mode

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/explore/ExploreViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/explore/ExploreFragment.kt`
- Modify: `app/src/main/res/layout/fragment_explore.xml` (add `chipForYou`)
- Modify: `app/src/main/java/com/example/calories/ui/explore/RecipeCardAdapter.kt` + `item_recipe_card.xml` (optional `tvSourceBadge`)
- Modify: `app/src/main/java/com/example/calories/ui/MainActivity.kt` (`openExploreForYou`)
- Modify: strings EN/VI

**Interfaces:**
- Consumes: `MealRecommendFacade`, `FoodRepository` / goals / exercise for today’s sums, `AuthRepository` or `ActiveUserIdProvider`, language prefs
- Produces: `ExploreUiState.mode: ExploreMode` (`BROWSE` | `FOR_YOU`), `forYouRecipes: UiState<List<RecommendedRecipe>>`, `isGenerating: Boolean`

- [ ] **Step 1: Add `chipForYou` + strings**

EN: `filter_for_you` = "For you"; `explore_for_you_empty_near_goal` = "You're close to your calorie goal"; `explore_generating_recipe` = "Generating a recipe…"; `recipe_badge_ai` = "AI"; `recipe_badge_fit` = "Fits macros"

VI: matching translations.

- [ ] **Step 2: ViewModel For you load**

When For you selected (or `enterForYouMode()` called from MainActivity):
1. Load today’s food + burned + daily goal (same sources Home uses)
2. `macroTargets = CalorieCalculator.macroTargetsFor(dailyGoal)`
3. `remaining = RemainingMacrosCalculator.compute(...)`
4. Call `mealRecommendFacade.recommend(...)`
5. Expose success/error; set `isGenerating` true only while Gemini path may run (facade can expose progress later; v1: single loading then result)

Add `fun enterForYouMode()` that selects For you and triggers load.

Session rate limit: remember `geminiUsedThisSession` in ViewModel; if facade is called twice, still OK to search DB again but pass a flag `allowGemini: Boolean` into facade — add optional param default true; second For-you entry in same VM sets false after first Gemini attempt. Simpler v1: one `allowGemini` flip after first `recommend` completes.

- [ ] **Step 3: MainActivity.openExploreForYou()**

Mirror `openProgressTab()` but `showTab(TAG_EXPLORE)` then:

```kotlin
(supportFragmentManager.findFragmentByTag(TAG_EXPLORE) as? ExploreFragment)
    ?.enterForYouMode()
```

If fragment not yet created, set a pending flag on Activity that `ExploreFragment.onViewCreated` consumes.

- [ ] **Step 4: Wire UI**

Chip selects For you; list binds `RecommendedRecipe.recipe` and shows badge from `source`. Retry calls recommend again.

- [ ] **Step 5: Manual smoke + commit**

```bash
git add app/src/main/java/com/example/calories/ui/explore app/src/main/java/com/example/calories/ui/MainActivity.kt app/src/main/res/layout/fragment_explore.xml app/src/main/res/layout/item_recipe_card.xml app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(explore): add For you smart meal mode"
```

---

### Task 9: Home meal CTA

**Files:**
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeDashboardModels.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/example/calories/ui/home/HomeFragment.kt`
- Modify: home layout include (or `fragment_home.xml`) — add CTA button/chip
- Modify: strings EN/VI

**Interfaces:**
- Consumes: same daily totals already in `buildUiState`
- Produces: `HomeUiState.showMealRecommendCta: Boolean`, `mealRecommendGap: MacroGap?`

- [ ] **Step 1: Compute CTA visibility in `buildUiState`**

```kotlin
val remaining = RemainingMacrosCalculator.compute(...)
val showMealRecommendCta = isSignedIn && remaining.remainingKcal >= MealRecommendConstants.MIN_REMAINING_KCAL
```

- [ ] **Step 2: UI + click**

Button text by gap:
- PROTEIN → `@string/home_meal_cta_protein`
- CARBS → `@string/home_meal_cta_carbs`
- FAT → `@string/home_meal_cta_fat`
- CALORIES → `@string/home_meal_cta_calories`

Click: `(activity as? MainActivity)?.openExploreForYou()`

Place below insight callout when both visible (per spec).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/calories/ui/home app/src/main/res/layout app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml
git commit -m "feat(home): add smart meal recommend CTA"
```

---

### Task 10: End-to-end verification

**Files:** none new (verification only)

- [ ] **Step 1: Run all recommend unit tests**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.calories.recommend.*"
```

Expected: all PASS

- [ ] **Step 2: Manual checklist**

1. Sign in; log food so remaining ≥ 150 with a clear protein gap → Home CTA visible  
2. Tap CTA → Explore For you → ≤3 recipes  
3. With sparse catalog / tight band, confirm Gemini top-up and new row in Supabase `recipes` with `source=ai`  
4. Near goal (&lt;150 remaining) → CTA hidden; For you empty near-goal copy  
5. Airplane mode → error + retry, no crash  
6. Open AI recipe detail → portion scale + add to meal still works  
7. EN/VI language switch updates CTA / For you copy  

- [ ] **Step 3: Final commit only if verification fixes were needed**

---

## Self-review (plan vs spec)

| Spec requirement | Task |
|------------------|------|
| Home CTA + Explore For you | 8, 9 |
| Dominant macro gap scoring | 1, 2 |
| DB search then Gemini if &lt; N | 5, 6, 7 |
| Shared catalog insert | 4, 5 |
| No fridge | Global constraints |
| Per-100g nutrition | Prompt in 6 + existing detail |
| Errors / partial Gemini | 7 |
| Unit tests calculator/scorer/parser/facade | 1–3, 7 |
| RLS + source columns | 4 |
| EN/VI strings | 8, 9 |

No TBD placeholders left in task steps. Types (`RemainingMacros`, `RecipeDraft`, `RecommendedRecipe`, facade `recommend`) are consistent across tasks.
