package com.example.calories.ui.fridge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemFridgeIngredientBinding
import com.example.calories.model.FridgeIngredient
import com.example.calories.util.DateTimeUtils

class FridgeIngredientAdapter :
    ListAdapter<FridgeIngredient, FridgeIngredientAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFridgeIngredientBinding.inflate(
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
        private val binding: ItemFridgeIngredientBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(ingredient: FridgeIngredient) {
            binding.tvIngredientName.text = ingredient.name
            binding.tvQuantity.text = binding.root.context.getString(
                R.string.quantity_unit_format,
                ingredient.quantity,
                ingredient.unit,
            )
            val expiry = ingredient.expiryDate
            if (expiry.isNullOrBlank()) {
                binding.tvExpiry.visibility = View.GONE
            } else {
                binding.tvExpiry.visibility = View.VISIBLE
                binding.tvExpiry.text = binding.root.context.getString(
                    R.string.expiry_format,
                    DateTimeUtils.formatExpiry(expiry),
                )
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<FridgeIngredient>() {
        override fun areItemsTheSame(
            oldItem: FridgeIngredient,
            newItem: FridgeIngredient,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: FridgeIngredient,
            newItem: FridgeIngredient,
        ): Boolean = oldItem == newItem
    }
}
