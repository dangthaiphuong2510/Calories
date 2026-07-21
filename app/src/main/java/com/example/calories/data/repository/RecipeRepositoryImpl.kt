package com.example.calories.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.calories.model.ExploreRecipeFilter
import com.example.calories.model.Recipe
import com.example.calories.model.RecipeDto
import com.example.calories.model.RecipeNutrientTag
import com.example.calories.model.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val supabase: SupabaseClient,
) : RecipeRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun fetchRecipes(
        query: String,
        filter: ExploreRecipeFilter,
    ): Result<List<Recipe>> = runCatching {
        val trimmed = query.trim()
        val rows = supabase.from(RECIPES_TABLE)
            .select(recipeListColumns) {
                filter {
                    if (trimmed.isNotEmpty()) {
                        ilike("name", "%$trimmed%")
                    }
                    applyKcalServerFilter(filter)
                }
                order(column = "name", order = Order.ASCENDING)
                limit(LIST_LIMIT)
            }
            .decodeList<RecipeDto>()

        rows.map { it.toDomain() }
            .let { applyClientFilter(it, filter) }
    }.onFailure { error ->
        Log.e(TAG, "Failed to fetch recipes query=$query filter=$filter", error)
    }

    override suspend fun getRecipeById(recipeId: String): Result<Recipe> = runCatching {
        val numericId = recipeId.toLongOrNull()
            ?: error("Invalid recipe id: $recipeId")

        val row = supabase.from(RECIPES_TABLE)
            .select(recipeDetailColumns) {
                filter {
                    eq("id", numericId)
                }
                limit(1)
            }
            .decodeSingle<RecipeDto>()

        row.toDomain()
    }.onFailure { error ->
        Log.e(TAG, "Failed to fetch recipe id=$recipeId", error)
    }

    override fun isRecipeUnlocked(recipeId: String): Boolean {
        return prefs.getBoolean(recipeId, false)
    }

    override fun unlockRecipe(recipeId: String) {
        prefs.edit().putBoolean(recipeId, true).apply()
    }

    private fun applyClientFilter(
        recipes: List<Recipe>,
        filter: ExploreRecipeFilter,
    ): List<Recipe> = recipes.filter { it.matchesExploreFilter(filter) }

    private companion object {
        const val TAG = "RecipeRepository"
        const val RECIPES_TABLE = "recipes"
        const val PREFS_NAME = "unlocked_recipes_pref"
        const val LIST_LIMIT = 80L

        val recipeListColumns = Columns.raw(
            """
            id,
            name,
            image_url,
            total_kcal,
            difficulty,
            prep_time,
            cook_time,
            recipe_macros(carbs_g, protein_g, fat_g),
            recipe_ingredients(id, recipe_id, name, amount, unit, kcal)
            """.trimIndent(),
        )

        val recipeDetailColumns = Columns.raw(
            """
            id,
            name,
            image_url,
            total_kcal,
            difficulty,
            prep_time,
            cook_time,
            recipe_macros(carbs_g, protein_g, fat_g),
            recipe_ingredients(id, recipe_id, name, amount, unit, kcal),
            recipe_steps(id, recipe_id, step_number, description)
            """.trimIndent(),
        )
    }
}

private fun io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder.applyKcalServerFilter(
    filter: ExploreRecipeFilter,
) {
    when (filter) {
        ExploreRecipeFilter.UNDER_200 -> lt("total_kcal", 200)
        ExploreRecipeFilter.FROM_200_TO_400 -> {
            and {
                gte("total_kcal", 200)
                lte("total_kcal", 400)
            }
        }
        ExploreRecipeFilter.FROM_400_TO_600 -> {
            and {
                gte("total_kcal", 400)
                lte("total_kcal", 600)
            }
        }
        ExploreRecipeFilter.OVER_600 -> gt("total_kcal", 600)
        else -> Unit
    }
}

private fun Recipe.matchesExploreFilter(filter: ExploreRecipeFilter): Boolean {
    val calories = totalKcal
    return when (filter) {
        ExploreRecipeFilter.ALL -> true
        ExploreRecipeFilter.UNDER_200 -> calories < 200
        ExploreRecipeFilter.FROM_200_TO_400 -> calories >= 200 && calories <= 400
        ExploreRecipeFilter.FROM_400_TO_600 -> calories >= 400 && calories <= 600
        ExploreRecipeFilter.OVER_600 -> calories > 600
        ExploreRecipeFilter.HIGH_PROTEIN -> nutrientTag == RecipeNutrientTag.HIGH_PROTEIN
        ExploreRecipeFilter.LOW_CARBS -> nutrientTag == RecipeNutrientTag.LOW_CARBS
        ExploreRecipeFilter.LOW_FAT -> nutrientTag == RecipeNutrientTag.LOW_FAT
    }
}