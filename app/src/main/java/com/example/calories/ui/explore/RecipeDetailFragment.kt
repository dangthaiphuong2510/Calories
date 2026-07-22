package com.example.calories.ui.explore

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import com.example.calories.R
import com.example.calories.ads.FullscreenAdWindowHelper
import com.example.calories.databinding.BottomSheetSelectMealTypeBinding
import com.example.calories.databinding.FragmentRecipeDetailBinding
import com.example.calories.databinding.ItemMealTypeOptionBinding
import com.example.calories.model.Recipe
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.UiState
import com.example.calories.ui.common.collectLatestStarted
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RecipeDetailFragment : BaseFragment<FragmentRecipeDetailBinding>() {

    private val viewModel: RecipeDetailViewModel by viewModels()
    private val ingredientAdapter = RecipeIngredientAdapter()
    private val stepAdapter = RecipeStepAdapter()

    private var suppressPortionWatcher = false
    private var rewardedAd: RewardedAd? = null

    private val testAdUnitId = "ca-app-pub-3940256099942544/5224354917"

    private val portionWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressPortionWatcher) {
                viewModel.onPortionChanged(s?.toString().orEmpty())
            }
        }
    }

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentRecipeDetailBinding = FragmentRecipeDetailBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadRewardedAd()
        setupUi()
        observeViewModel()
    }

    private fun setupUi() {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnRetry.setOnClickListener { viewModel.retry() }
        binding.etPortion.addTextChangedListener(portionWatcher)
        binding.btnAddToMeal.setOnClickListener { showMealTypePicker() }
        binding.btnUnlockRecipe.setOnClickListener { showRewardedAdToUnlock() }

        binding.rvIngredients.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIngredients.adapter = ingredientAdapter
        binding.rvSteps.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSteps.adapter = stepAdapter

        binding.chartMacros.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setUsePercentValues(true)
            setDrawEntryLabels(false)
            holeRadius = 62f
            transparentCircleRadius = 66f
            setHoleColor(Color.TRANSPARENT)
            setDrawCenterText(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            when (val recipeState = state.recipeState) {
                is UiState.Loading -> {
                    binding.progressLoading.visibility = View.VISIBLE
                    binding.contentContainer.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                }

                is UiState.Error -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.contentContainer.visibility = View.GONE
                    binding.errorContainer.visibility = View.VISIBLE
                    binding.tvError.text = recipeState.message
                }

                is UiState.Success -> {
                    binding.progressLoading.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                    binding.contentContainer.visibility = View.VISIBLE
                    bindRecipeHeader(recipeState.data)
                }
            }

            binding.tvCalories.text = getString(R.string.meal_calories_format, state.calories)
            binding.tvProtein.text = getString(R.string.macro_pill_protein, state.protein)
            binding.tvCarb.text = getString(R.string.macro_pill_carb, state.carb)
            binding.tvFat.text = getString(R.string.macro_pill_fat, state.fat)
            binding.btnAddToMeal.isEnabled = !state.isSaving
            ingredientAdapter.submitList(state.scaledIngredients)
            bindChart(state)

            // Update unlock overlay visibility based on state
            binding.layoutUnlockOverlay.visibility =
                if (state.isUnlocked) View.GONE else View.VISIBLE

            val portionText = formatPortion(state.portionGrams)
            if (binding.etPortion.text?.toString() != portionText) {
                suppressPortionWatcher = true
                binding.etPortion.setText(portionText)
                binding.etPortion.setSelection(portionText.length)
                suppressPortionWatcher = false
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

        viewLifecycleOwner.collectLatestStarted(viewModel.addedToMeal) {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            requireContext(),
            testAdUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    private fun showRewardedAdToUnlock() {
        val ad = rewardedAd
        if (ad != null) {
            val activity = requireActivity()
            ad.fullScreenContentCallback = FullscreenAdWindowHelper.wrapCallback(
                activity,
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        rewardedAd = null
                        loadRewardedAd()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        rewardedAd = null
                        loadRewardedAd()
                    }
                },
            )

            FullscreenAdWindowHelper.enterFullscreenAdMode(activity)
            ad.show(activity) { _ ->
                viewModel.unlockRecipe()
                Toast.makeText(requireContext(), "Instructions unlocked!", Toast.LENGTH_SHORT)
                    .show()
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Ad is loading, please try again shortly!",
                Toast.LENGTH_SHORT
            ).show()
            loadRewardedAd()
        }
    }

    private fun bindRecipeHeader(recipe: Recipe) {
        binding.tvRecipeName.text = recipe.name
        binding.tvRecipeMeta.text = buildRecipeMeta(recipe)
        binding.ivRecipeHero.load(recipe.imageUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_food_placeholder_24)
            error(R.drawable.ic_food_placeholder_24)
        }
        stepAdapter.submitList(recipe.steps)
    }

    private fun buildRecipeMeta(recipe: Recipe): String {
        val parts = buildList {
            recipe.difficulty?.takeIf { it.isNotBlank() }?.let { add(it) }
            recipe.prepTimeMinutes?.takeIf { it > 0 }?.let {
                add(getString(R.string.recipe_prep_time_format, it))
            }
            recipe.cookTimeMinutes?.takeIf { it > 0 }?.let {
                add(getString(R.string.recipe_cook_time_format, it))
            }
        }
        return parts.joinToString(" · ")
    }

    private fun bindChart(state: RecipeDetailUiState) {
        val entries = buildList {
            if (state.protein > 0) add(PieEntry(state.protein.toFloat(), "P"))
            if (state.carb > 0) add(PieEntry(state.carb.toFloat(), "C"))
            if (state.fat > 0) add(PieEntry(state.fat.toFloat(), "F"))
        }
        if (entries.isEmpty()) {
            binding.chartMacros.clear()
            binding.chartMacros.centerText = "—"
            binding.chartMacros.invalidate()
            return
        }
        val colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.macro_protein),
            ContextCompat.getColor(requireContext(), R.color.macro_carb),
            ContextCompat.getColor(requireContext(), R.color.macro_fat),
        )
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors.take(entries.size)
            setDrawValues(false)
            sliceSpace = 2f
        }
        binding.chartMacros.data = PieData(dataSet)
        binding.chartMacros.centerText = getString(R.string.kcal_value_format, state.calories)
        binding.chartMacros.invalidate()
    }

    private fun showMealTypePicker() {
        val sheetBinding = BottomSheetSelectMealTypeBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(sheetBinding.root)

        bindMealOption(
            binding = sheetBinding.optionBreakfast,
            iconRes = R.drawable.ic_meal_breakfast_48,
            labelRes = R.string.meal_breakfast,
        ) {
            dialog.dismiss()
            viewModel.addToMeal(MealType.BREAKFAST)
        }
        bindMealOption(
            binding = sheetBinding.optionLunch,
            iconRes = R.drawable.ic_meal_lunch_48,
            labelRes = R.string.meal_lunch,
        ) {
            dialog.dismiss()
            viewModel.addToMeal(MealType.LUNCH)
        }
        bindMealOption(
            binding = sheetBinding.optionDinner,
            iconRes = R.drawable.ic_meal_dinner_48,
            labelRes = R.string.meal_dinner,
        ) {
            dialog.dismiss()
            viewModel.addToMeal(MealType.DINNER)
        }
        bindMealOption(
            binding = sheetBinding.optionSnacks,
            iconRes = R.drawable.ic_meal_snack_48,
            labelRes = R.string.meal_snacks,
        ) {
            dialog.dismiss()
            viewModel.addToMeal(MealType.SNACKS)
        }

        dialog.show()
    }

    private fun bindMealOption(
        binding: ItemMealTypeOptionBinding,
        iconRes: Int,
        labelRes: Int,
        onClick: () -> Unit,
    ) {
        binding.ivMealIcon.setImageResource(iconRes)
        binding.tvMealLabel.setText(labelRes)
        binding.root.setOnClickListener { onClick() }
    }

    private fun formatPortion(value: Double): String {
        return if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }

    companion object {
        fun newInstance(recipeId: String): RecipeDetailFragment = RecipeDetailFragment().apply {
            arguments = bundleOf(RecipeDetailViewModel.ARG_RECIPE_ID to recipeId)
        }
    }
}