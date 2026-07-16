package com.example.calories.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemRecipeIngredientBinding
import com.example.calories.model.RecipeIngredient
import kotlin.math.roundToInt

data class ScaledRecipeIngredient(
    val id: String,
    val name: String,
    val amount: Double,
    val unit: String,
    val kcal: Int,
)

class RecipeIngredientAdapter :
    ListAdapter<ScaledRecipeIngredient, RecipeIngredientAdapter.IngredientViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemRecipeIngredientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class IngredientViewHolder(
        private val binding: ItemRecipeIngredientBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScaledRecipeIngredient) {
            binding.tvIngredientName.text = item.name
            binding.tvIngredientAmount.text = binding.root.context.getString(
                R.string.recipe_ingredient_amount_format,
                formatAmount(item.amount),
                item.unit,
            )
            binding.tvIngredientKcal.text = binding.root.context.getString(
                R.string.recipe_ingredient_kcal_format,
                item.kcal,
            )
        }

        private fun formatAmount(value: Double): String {
            return if (value % 1.0 == 0.0) value.roundToInt().toString() else "%.1f".format(value)
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<ScaledRecipeIngredient>() {
        override fun areItemsTheSame(
            oldItem: ScaledRecipeIngredient,
            newItem: ScaledRecipeIngredient,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: ScaledRecipeIngredient,
            newItem: ScaledRecipeIngredient,
        ): Boolean = oldItem == newItem
    }
}

fun RecipeIngredient.scaled(scale: Double): ScaledRecipeIngredient = ScaledRecipeIngredient(
    id = id,
    name = name,
    amount = amount * scale,
    unit = unit,
    kcal = (kcal * scale).roundToInt(),
)
