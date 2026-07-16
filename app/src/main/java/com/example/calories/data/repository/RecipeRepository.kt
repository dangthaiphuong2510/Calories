package com.example.calories.data.repository

import com.example.calories.model.ExploreRecipeFilter
import com.example.calories.model.Recipe

interface RecipeRepository {
    suspend fun fetchRecipes(
        query: String = "",
        filter: ExploreRecipeFilter = ExploreRecipeFilter.ALL,
    ): Result<List<Recipe>>

    suspend fun getRecipeById(recipeId: String): Result<Recipe>
}
