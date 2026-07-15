package com.example.calories.ui.exercise

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.databinding.ItemPresetExerciseBinding
import com.example.calories.model.ExercisePreset

class ExercisePresetAdapter(
    private val onClick: (ExercisePreset) -> Unit,
) : ListAdapter<ExercisePreset, ExercisePresetAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemPresetExerciseBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(
        private val binding: ItemPresetExerciseBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ExercisePreset) {
            binding.tvPresetName.text = item.name
            binding.tvPresetMeta.text = item.subtitle
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ExercisePreset>() {
        override fun areItemsTheSame(oldItem: ExercisePreset, newItem: ExercisePreset) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ExercisePreset, newItem: ExercisePreset) =
            oldItem == newItem
    }
}
