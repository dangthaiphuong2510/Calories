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
import com.example.calories.model.ExploreRecipeFilter
import com.example.calories.model.Recipe
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.UiState
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ExploreFragment : BaseFragment<FragmentExploreBinding>() {

    private val viewModel: ExploreViewModel by viewModels()
    private val adapter = RecipeCardAdapter { recipe -> openRecipeDetail(recipe) }

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
        binding.rvRecipes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecipes.adapter = adapter
        binding.etSearch.addTextChangedListener(queryWatcher)
        binding.btnRetry.setOnClickListener { viewModel.retry() }

        binding.chipGroupFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when (checkedIds.firstOrNull()) {
                R.id.chipKcalUnder200 -> ExploreRecipeFilter.UNDER_200
                R.id.chipKcal200400 -> ExploreRecipeFilter.FROM_200_TO_400
                R.id.chipKcal400600 -> ExploreRecipeFilter.FROM_400_TO_600
                R.id.chipKcalOver600 -> ExploreRecipeFilter.OVER_600
                R.id.chipHighProtein -> ExploreRecipeFilter.HIGH_PROTEIN
                R.id.chipLowCarbs -> ExploreRecipeFilter.LOW_CARBS
                R.id.chipLowFat -> ExploreRecipeFilter.LOW_FAT
                else -> ExploreRecipeFilter.ALL
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

            when (val recipesState = state.recipesState) {
                is UiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.rvRecipes.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                    adapter.submitList(recipesState.data)
                    val isEmpty = recipesState.data.isEmpty()
                    binding.tvEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
                    binding.rvRecipes.visibility = if (isEmpty) View.GONE else View.VISIBLE
                }
                is UiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.rvRecipes.visibility = View.GONE
                    binding.tvEmpty.visibility = View.GONE
                    binding.errorContainer.visibility = View.VISIBLE
                    binding.tvError.text = recipesState.message
                }
            }
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

    private fun openRecipeDetail(recipe: Recipe) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostFragment, RecipeDetailFragment.newInstance(recipe.id))
            .addToBackStack("recipe_detail")
            .commit()
    }
}
