package com.example.calories.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.R
import com.example.calories.databinding.ItemWeightEntryBinding
import com.example.calories.model.WeightEntry
import com.example.calories.util.DateTimeUtils

class WeightEntryAdapter : ListAdapter<WeightEntry, WeightEntryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeightEntryBinding.inflate(
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
        private val binding: ItemWeightEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: WeightEntry) {
            binding.tvWeightDate.text = DateTimeUtils.formatDisplayDate(entry.recordedAt)
            binding.tvWeightValue.text = binding.root.context.getString(
                R.string.weight_kg_format,
                entry.weightKg,
            )
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<WeightEntry>() {
        override fun areItemsTheSame(oldItem: WeightEntry, newItem: WeightEntry): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: WeightEntry, newItem: WeightEntry): Boolean =
            oldItem == newItem
    }
}
