package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeDto(
    val id: Long,
    val name: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("total_kcal") val totalKcal: Double = 0.0,
    val difficulty: String? = null,
    @SerialName("prep_time") val prepTime: Int? = null,
    @SerialName("cook_time") val cookTime: Int? = null,
    // 1:1 FK → PostgREST returns object; 1:N → arrays
    @SerialName("recipe_macros") val macros: RecipeMacroDto? = null,
    @SerialName("recipe_ingredients") val ingredients: List<RecipeIngredientDto> = emptyList(),
    @SerialName("recipe_steps") val steps: List<RecipeStepDto> = emptyList(),
)

@Serializable
data class RecipeMacroDto(
    @SerialName("recipe_id") val recipeId: Long? = null,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
)

@Serializable
data class RecipeIngredientDto(
    val id: Long,
    @SerialName("recipe_id") val recipeId: Long? = null,
    val name: String,
    val amount: Double = 0.0,
    val unit: String = "",
    val kcal: Double = 0.0,
)

@Serializable
data class RecipeStepDto(
    val id: Long,
    @SerialName("recipe_id") val recipeId: Long? = null,
    @SerialName("step_number") val stepNumber: Int,
    val description: String,
)


data class Recipe(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val totalKcal: Double,
    val difficulty: String?,
    val prepTimeMinutes: Int?,
    val cookTimeMinutes: Int?,
    val macros: RecipeMacros?,
    val ingredients: List<RecipeIngredient>,
    val steps: List<RecipeStep>,
) {
    val ingredientCount: Int get() = ingredients.size

    val nutrientTag: RecipeNutrientTag
        get() {
            val protein = macros?.proteinG ?: 0.0
            val carbs = macros?.carbsG ?: 0.0
            val fat = macros?.fatG ?: 0.0
            return when {
                protein >= 20.0 && protein >= carbs && protein >= fat -> RecipeNutrientTag.HIGH_PROTEIN
                carbs <= 10.0 -> RecipeNutrientTag.LOW_CARBS
                fat <= 5.0 -> RecipeNutrientTag.LOW_FAT
                else -> RecipeNutrientTag.BALANCED
            }
        }
}

data class RecipeMacros(
    val carbsG: Double,
    val proteinG: Double,
    val fatG: Double,
)

data class RecipeIngredient(
    val id: String,
    val name: String,
    val amount: Double,
    val unit: String,
    val kcal: Double,
)

data class RecipeStep(
    val id: String,
    val stepNumber: Int,
    val description: String,
)

fun RecipeDto.toDomain(): Recipe = Recipe(
    id = id.toString(),
    name = name,
    imageUrl = imageUrl,
    totalKcal = totalKcal,
    difficulty = difficulty,
    prepTimeMinutes = prepTime,
    cookTimeMinutes = cookTime,
    macros = macros?.toDomain(),
    ingredients = ingredients.map { it.toDomain() },
    steps = steps.map { it.toDomain() }.sortedBy { it.stepNumber },
)

private fun RecipeMacroDto.toDomain(): RecipeMacros = RecipeMacros(
    carbsG = carbsG,
    proteinG = proteinG,
    fatG = fatG,
)

private fun RecipeIngredientDto.toDomain(): RecipeIngredient = RecipeIngredient(
    id = id.toString(),
    name = name,
    amount = amount,
    unit = unit,
    kcal = kcal,
)

private fun RecipeStepDto.toDomain(): RecipeStep = RecipeStep(
    id = id.toString(),
    stepNumber = stepNumber,
    description = description,
)