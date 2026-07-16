package com.example.calories.ui.explore

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.databinding.ItemRecipeStepBinding
import com.example.calories.model.RecipeStep

class RecipeStepAdapter :
    ListAdapter<RecipeStep, RecipeStepAdapter.StepViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemRecipeStepBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class StepViewHolder(
        private val binding: ItemRecipeStepBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(step: RecipeStep) {
            binding.tvStepNumber.text = step.stepNumber.toString()
            binding.tvStepDescription.text = step.description
        }
    }

    private companion object DiffCallback : DiffUtil.ItemCallback<RecipeStep>() {
        override fun areItemsTheSame(oldItem: RecipeStep, newItem: RecipeStep): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RecipeStep, newItem: RecipeStep): Boolean =
            oldItem == newItem
    }
}
