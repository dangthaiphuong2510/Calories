package com.example.calories.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.calories.R
import com.example.calories.databinding.ItemRecipeCardBinding
import com.example.calories.model.Recipe
import com.example.calories.model.RecipeNutrientTag
import kotlin.math.roundToInt

class RecipeCardAdapter(
    private val onRecipeClick: (Recipe) -> Unit,
) : ListAdapter<Recipe, RecipeCardAdapter.RecipeViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val binding = ItemRecipeCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return RecipeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class RecipeViewHolder(
        private val binding: ItemRecipeCardBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(recipe: Recipe) {
            val context = binding.root.context
            binding.tvRecipeName.text = recipe.name
            binding.tvRecipeKcal.text = context.getString(
                R.string.recipe_kcal_format,
                recipe.totalKcal.roundToInt(),
            )
            binding.tvIngredientCount.text = context.resources.getQuantityString(
                R.plurals.recipe_ingredient_count,
                recipe.ingredientCount,
                recipe.ingredientCount,
            )
            binding.chipNutrientTag.text = nutrientTagLabel(context, recipe.nutrientTag)

            binding.ivRecipeImage.load(recipe.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_food_placeholder_24)
                error(R.drawable.ic_food_placeholder_24)
            }

            binding.root.setOnClickListener { onRecipeClick(recipe) }
        }

        private fun nutrientTagLabel(
            context: android.content.Context,
            tag: RecipeNutrientTag,
        ): String = when (tag) {
            RecipeNutrientTag.HIGH_PROTEIN -> context.getString(R.string.filter_high_protein)
            RecipeNutrientTag.LOW_CARBS -> context.getString(R.string.filter_low_carbs)
            RecipeNutrientTag.LOW_FAT -> context.getString(R.string.filter_low_fat)
            RecipeNutrientTag.BALANCED -> context.getString(R.string.recipe_tag_balanced)
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<Recipe>() {
        override fun areItemsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Recipe, newItem: Recipe): Boolean =
            oldItem == newItem
    }
}
