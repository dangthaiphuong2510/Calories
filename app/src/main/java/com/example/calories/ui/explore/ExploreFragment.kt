package com.example.calories.ui.explore

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.FragmentExploreBinding
import com.example.calories.model.ExploreKcalFilter
import com.example.calories.model.FoodDictionaryItem
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.food.FoodDetailActivity
import com.example.calories.ui.food.FoodDictionaryAdapter
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreBinding>() {

    private val viewModel: ExploreViewModel by viewModels()
    private val adapter = FoodDictionaryAdapter { item -> openFoodDetail(item) }

    private var suppressQueryWatcher = false

    private val queryWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressQueryWatcher) {
                viewModel.onQueryChanged(s?.toString().orEmpty())
            }
        }
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentExploreBinding = FragmentExploreBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        binding.rvFoods.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFoods.adapter = adapter
        binding.etSearch.addTextChangedListener(queryWatcher)

        binding.chipGroupKcalFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipKcalUnder200 -> ExploreKcalFilter.UNDER_200
                R.id.chipKcal200400 -> ExploreKcalFilter.FROM_200_TO_400
                R.id.chipKcal400600 -> ExploreKcalFilter.FROM_400_TO_600
                R.id.chipKcalOver600 -> ExploreKcalFilter.OVER_600
                else -> ExploreKcalFilter.ALL
            }
            viewModel.onFilterSelected(filter)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            if (binding.etSearch.text?.toString() != state.query) {
                suppressQueryWatcher = true
                binding.etSearch.setText(state.query)
                binding.etSearch.setSelection(state.query.length)
                suppressQueryWatcher = false
            }

            adapter.submitList(state.results)
            binding.progressLoading.visibility =
                if (state.isLoading) View.VISIBLE else View.GONE
            binding.tvEmpty.visibility =
                if (state.isEmpty && !state.isLoading) View.VISIBLE else View.GONE
            binding.rvFoods.visibility =
                if (state.isEmpty && !state.isLoading) View.GONE else View.VISIBLE
        }

        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(requireContext(), event.text, Toast.LENGTH_SHORT).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(requireContext(), event.resId, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openFoodDetail(item: FoodDictionaryItem) {
        startActivity(
            FoodDetailActivity.intent(
                context = requireContext(),
                name = item.name,
                calories = item.caloriesInt,
                protein = item.proteinGrams,
                carb = item.carbGrams,
                fat = item.fatGrams,
                servingGrams = 100.0,
                mealType = MealType.SNACK,
                selectedDate = DateTimeUtils.today(),
                viewOnly = false,
            ),
        )
    }
}
