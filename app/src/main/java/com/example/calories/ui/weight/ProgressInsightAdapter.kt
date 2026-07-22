package com.example.calories.ui.weight

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.calories.databinding.ItemProgressInsightBinding
import com.example.calories.insights.InsightAction
import com.example.calories.insights.ProgressInsight
import com.example.calories.insights.ProgressInsightUiMapper

class ProgressInsightAdapter(
    private val onInsightClick: (ProgressInsight) -> Unit,
) : ListAdapter<ProgressInsight, ProgressInsightAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProgressInsightBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding, onInsightClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemProgressInsightBinding,
        private val onInsightClick: (ProgressInsight) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(insight: ProgressInsight) {
            val context = binding.root.context
            binding.tvInsightTitle.setText(ProgressInsightUiMapper.titleRes(insight.id))
            val bodyRes = ProgressInsightUiMapper.bodyRes(insight.id)
            binding.tvInsightBody.text = if (insight.formatArgs.isEmpty()) {
                context.getString(bodyRes)
            } else {
                context.getString(bodyRes, *insight.formatArgs.toTypedArray())
            }
            val isClickable = insight.action is InsightAction.OpenWeightLog
            binding.root.isClickable = isClickable
            binding.root.isFocusable = isClickable
            if (isClickable) {
                binding.root.setOnClickListener { onInsightClick(insight) }
            } else {
                binding.root.setOnClickListener(null)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ProgressInsight>() {
        override fun areItemsTheSame(oldItem: ProgressInsight, newItem: ProgressInsight): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ProgressInsight, newItem: ProgressInsight): Boolean =
            oldItem == newItem
    }
}
