package com.example.calories.ui.nutrition

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.databinding.FragmentFoodHistoryBinding
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.nutrition.adapter.FoodHistoryAdapter
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FoodHistoryFragment : BaseFragment<FragmentFoodHistoryBinding>() {

    private val viewModel: FoodHistoryViewModel by viewModels()
    private val adapter = FoodHistoryAdapter()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFoodHistoryBinding = FragmentFoodHistoryBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFoodHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFoodHistory.adapter = adapter

        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            adapter.submitList(state.days)
            val isEmpty = state.days.isEmpty()
            binding.tvEmptyHistory.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvFoodHistory.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }
}
