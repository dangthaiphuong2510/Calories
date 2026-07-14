package com.example.calories.ui.nutrition.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemFoodEntryBinding
import com.example.calories.model.FoodEntry
import com.example.calories.model.enums.MealType

class FoodEntryAdapter : ListAdapter<FoodEntry, FoodEntryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFoodEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFoodEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: FoodEntry) {
            val context = binding.root.context
            binding.tvFoodName.text = entry.name
            binding.tvCalories.text = entry.calories.toString()
            binding.tvMacros.text = context.getString(
                R.string.macros_line_format,
                entry.protein,
                entry.carb,
                entry.fat,
            )
            binding.chipMealType.text = mealTypeLabel(entry.mealType)
        }

        private fun mealTypeLabel(mealType: MealType): String {
            val res = when (mealType) {
                MealType.BREAKFAST -> R.string.meal_breakfast
                MealType.LUNCH -> R.string.meal_lunch
                MealType.DINNER -> R.string.meal_dinner
                MealType.SNACK -> R.string.meal_snack
            }
            return binding.root.context.getString(res)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FoodEntry>() {
        override fun areItemsTheSame(oldItem: FoodEntry, newItem: FoodEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FoodEntry, newItem: FoodEntry): Boolean =
            oldItem == newItem
    }
}
