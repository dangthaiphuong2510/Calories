package com.example.calories.ui.nutrition.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemFoodHistoryDayBinding

data class FoodHistoryDay(
    val dateLabel: String,
    val entryCount: Int,
    val totalCalories: Int,
    val protein: Double,
    val carb: Double,
    val fat: Double,
)

class FoodHistoryAdapter : ListAdapter<FoodHistoryDay, FoodHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFoodHistoryDayBinding.inflate(
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
        private val binding: ItemFoodHistoryDayBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(day: FoodHistoryDay) {
            val context = binding.root.context
            binding.tvHistoryDate.text = day.dateLabel
            binding.tvHistoryCalories.text = context.getString(
                R.string.history_calories_format,
                day.totalCalories,
            )
            binding.tvHistoryMeals.text = context.resources.getQuantityString(
                R.plurals.history_meals_count,
                day.entryCount,
                day.entryCount,
            )
            binding.tvHistoryMacros.text = context.getString(
                R.string.macros_line_format,
                day.protein,
                day.carb,
                day.fat,
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FoodHistoryDay>() {
        override fun areItemsTheSame(oldItem: FoodHistoryDay, newItem: FoodHistoryDay): Boolean =
            oldItem.dateLabel == newItem.dateLabel

        override fun areContentsTheSame(oldItem: FoodHistoryDay, newItem: FoodHistoryDay): Boolean =
            oldItem == newItem
    }
}
