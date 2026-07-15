package com.example.calories.ui.food

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemFoodDictionaryBinding
import com.example.calories.model.FoodDictionaryItem

class FoodDictionaryAdapter(
    private val onAddClick: (FoodDictionaryItem) -> Unit,
) : ListAdapter<FoodDictionaryItem, FoodDictionaryAdapter.FoodViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val binding = ItemFoodDictionaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return FoodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FoodViewHolder(
        private val binding: ItemFoodDictionaryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FoodDictionaryItem) {
            val context = binding.root.context
            binding.tvFoodName.text = item.name
            binding.tvFoodMeta.text = context.getString(
                R.string.search_food_meta_format,
                context.getString(R.string.default_portion_100g),
                item.caloriesInt,
            )
            binding.btnAddFood.setOnClickListener { onAddClick(item) }
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<FoodDictionaryItem>() {
        override fun areItemsTheSame(
            oldItem: FoodDictionaryItem,
            newItem: FoodDictionaryItem,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: FoodDictionaryItem,
            newItem: FoodDictionaryItem,
        ): Boolean = oldItem == newItem
    }
}
