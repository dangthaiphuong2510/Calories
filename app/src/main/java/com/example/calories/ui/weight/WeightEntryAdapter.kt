package com.example.calories.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.data.preferences.UnitSystem
import com.example.calories.databinding.ItemWeightEntryBinding
import com.example.calories.model.WeightEntry
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.UnitConverter

class WeightEntryAdapter : ListAdapter<WeightEntry, WeightEntryAdapter.ViewHolder>(DiffCallback) {

    private var unitSystem: UnitSystem = UnitSystem.METRIC

    fun submitList(list: List<WeightEntry>?, unit: UnitSystem) {
        val unitChanged = unitSystem != unit
        unitSystem = unit
        if (unitChanged) {
            // Force rebind so kg ↔ lb labels refresh even when entry data is unchanged.
            submitList(null) {
                submitList(list)
            }
        } else {
            submitList(list)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWeightEntryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), unitSystem)
    }

    class ViewHolder(
        private val binding: ItemWeightEntryBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(entry: WeightEntry, unitSystem: UnitSystem) {
            binding.tvWeightDate.text = DateTimeUtils.formatDisplayDate(entry.recordedAt)
            binding.tvWeightValue.text = UnitConverter.formatWeight(
                binding.root.context,
                entry.weightKg,
                unitSystem,
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
