package com.example.calories.ui.notifications

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.databinding.ItemScheduleTimeRowBinding

class ScheduleTimeAdapter(
    private val onTimeClick: (index: Int, time: String) -> Unit,
    private val onRemoveClick: (index: Int) -> Unit,
) : ListAdapter<String, ScheduleTimeAdapter.VH>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScheduleTimeRowBinding.inflate(
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
        private val binding: ItemScheduleTimeRowBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(time: String) {
            binding.tvScheduleTime.text = time
            binding.tvScheduleTime.setOnClickListener {
                val index = bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) {
                    onTimeClick(index, time)
                }
            }
            binding.btnRemoveTime.setOnClickListener {
                val index = bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) {
                    onRemoveClick(index)
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
    }
}
