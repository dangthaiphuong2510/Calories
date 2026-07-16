package com.example.calories.data.repository

import android.util.Log
import com.example.calories.model.ExploreRecipeFilter
import com.example.calories.model.Recipe
import com.example.calories.model.RecipeDto
import com.example.calories.model.RecipeNutrientTag
import com.example.calories.model.toDomain
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecipeRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : RecipeRepository {

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

    private fun applyClientFilter(
        recipes: List<Recipe>,
        filter: ExploreRecipeFilter,
    ): List<Recipe> {
        return when (filter) {
            ExploreRecipeFilter.HIGH_PROTEIN ->
                recipes.filter { it.nutrientTag == RecipeNutrientTag.HIGH_PROTEIN }
            ExploreRecipeFilter.LOW_CARBS ->
                recipes.filter { it.nutrientTag == RecipeNutrientTag.LOW_CARBS }
            ExploreRecipeFilter.LOW_FAT ->
                recipes.filter { it.nutrientTag == RecipeNutrientTag.LOW_FAT }
            else -> recipes
        }
    }

    private companion object {
        const val TAG = "RecipeRepository"
        const val RECIPES_TABLE = "recipes"
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
            gte("total_kcal", 200)
            lte("total_kcal", 400)
        }
        ExploreRecipeFilter.FROM_400_TO_600 -> {
            gte("total_kcal", 401)
            lte("total_kcal", 600)
        }
        ExploreRecipeFilter.OVER_600 -> gt("total_kcal", 600)
        else -> Unit
    }
}
