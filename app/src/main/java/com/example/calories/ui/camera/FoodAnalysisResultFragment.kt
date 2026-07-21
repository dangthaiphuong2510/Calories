package com.example.calories.ui.camera

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentFoodAnalysisResultBinding
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.model.enums.MealType
import com.example.calories.ui.MainActivity
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@AndroidEntryPoint
class FoodAnalysisResultFragment : BaseFragment<FragmentFoodAnalysisResultBinding>() {

    private val viewModel: FoodAnalysisResultViewModel by viewModels()
    private lateinit var mealLabels: List<String>
    private var suppressWatchers = false
    private var lastBoundIngredients: List<String>? = null

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFoodAnalysisResultBinding =
        FragmentFoodAnalysisResultBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mealLabels = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack),
        )
        binding.actMealType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mealLabels),
        )

        setupWatchers()
        binding.btnRetake.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnScanAgain.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSaveFood.setOnClickListener {
            syncMealTypeFromDropdown()
            viewModel.saveFood()
        }
        observeViewModel()

        val imagePath = requireArguments().getString(ARG_IMAGE_PATH).orEmpty()
        val preAnalyzedJson = requireArguments().getString(ARG_ANALYSIS_JSON)
        if (!preAnalyzedJson.isNullOrBlank()) {
            viewModel.applyPreAnalyzedResult(Json.decodeFromString<FoodAnalysisResult>(preAnalyzedJson))
        } else {
            viewModel.analyzeImage(imagePath)
        }
    }

    private fun setupWatchers() {
        binding.etFoodName.addTextChangedListener(simpleWatcher(viewModel::onFoodNameChanged))
        binding.etWeight.addTextChangedListener(simpleWatcher(viewModel::onWeightChanged))
        binding.actMealType.setOnItemClickListener { _, _, position, _ ->
            viewModel.onMealTypeChanged(MealType.entries.getOrElse(position) { MealType.SNACKS })
        }
    }

    private fun syncMealTypeFromDropdown() {
        val index = mealLabels.indexOf(binding.actMealType.text?.toString())
        if (index >= 0) {
            viewModel.onMealTypeChanged(MealType.entries[index])
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.progressAnalysis.isVisible = state.isAnalyzing
            val hasResult = state.analysis != null && !state.isAnalyzing
            binding.groupAnalysisContent.isVisible = hasResult && state.isFoodDetected
            binding.groupFoodNotDetected.isVisible = hasResult && !state.isFoodDetected
            binding.btnSaveFood.isEnabled = !state.isSaving

            if (state.analysis == null || !state.isFoodDetected) return@collectLatestStarted

            bindSummary(state)
            bindMacros(state)
            bindIngredients(state.ingredients)
            bindEditableFields(state)
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
                is UiEvent.MessageRes ->
                    Snackbar.make(binding.root, event.resId, Snackbar.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                is FoodAnalysisResultNavEvent.Saved -> {
                    parentFragmentManager.setFragmentResult(
                        HOME_FOOD_LOGGED_REQUEST,
                        Bundle().apply {
                            putString(HOME_FOOD_LOGGED_MEAL_TYPE, event.mealType.name)
                        },
                    )
                    (activity as? MainActivity)?.navigateToHome()
                }
            }
        }
    }

    private fun bindSummary(state: FoodAnalysisResultUiState) {
        binding.tvFoodNameSummary.text = state.foodName.ifBlank { getString(R.string.food_name) }
        binding.tvCaloriesValue.text = state.calories.toString()
        binding.progressCalories.progress = 100
        binding.tvDetectedWeight.text = getString(R.string.grams_value, state.weightGrams)
    }

    private fun bindMacros(state: FoodAnalysisResultUiState) {
        binding.tvProteinValue.text = getString(R.string.grams_value_decimal, state.proteinGrams)
        binding.tvCarbValue.text = getString(R.string.grams_value_decimal, state.carbsGrams)
        binding.tvFatValue.text = getString(R.string.grams_value_decimal, state.fatGrams)
        binding.progressProtein.progress = normalizeMacro(state.proteinGrams, PROTEIN_TARGET_G)
        binding.progressCarb.progress = normalizeMacro(state.carbsGrams, CARB_TARGET_G)
        binding.progressFat.progress = normalizeMacro(state.fatGrams, FAT_TARGET_G)
    }

    private fun bindIngredients(ingredients: List<String>) {
        if (ingredients == lastBoundIngredients) return
        lastBoundIngredients = ingredients

        binding.chipGroupIngredients.removeAllViews()
        val hasIngredients = ingredients.isNotEmpty()
        binding.chipGroupIngredients.isVisible = hasIngredients
        binding.tvNoIngredients.isVisible = !hasIngredients
        if (!hasIngredients) return

        ingredients.forEach { name ->
            binding.chipGroupIngredients.addView(
                Chip(requireContext()).apply {
                    text = name
                    isCheckable = false
                    isClickable = false
                    setEnsureMinTouchTargetSize(false)
                    chipBackgroundColor =
                        requireContext().getColorStateList(R.color.primary_light)
                    setTextColor(requireContext().getColor(R.color.primary_dark))
                },
            )
        }
    }

    private fun bindEditableFields(state: FoodAnalysisResultUiState) {
        suppressWatchers = true
        setTextIfChanged(binding.etFoodName, state.foodName)
        setTextIfChanged(binding.etCalories, state.caloriesText)
        setTextIfChanged(binding.etWeight, state.weightText)
        setTextIfChanged(binding.etProtein, state.proteinText)
        setTextIfChanged(binding.etCarb, state.carbsText)
        setTextIfChanged(binding.etFat, state.fatText)

        val mealLabel = mealLabels.getOrElse(state.mealType.ordinal) { mealLabels.last() }
        if (binding.actMealType.text?.toString() != mealLabel) {
            binding.actMealType.setText(mealLabel, false)
        }
        suppressWatchers = false
    }

    private fun setTextIfChanged(
        editText: android.widget.EditText,
        value: String,
    ) {
        if (editText.text?.toString() != value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }
    }

    private fun normalizeMacro(value: Float, target: Float): Int {
        return ((value / target) * 100).toInt().coerceIn(0, 100)
    }

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) {
            if (!suppressWatchers) onChange(s?.toString().orEmpty())
        }
    }

    companion object {
        const val HOME_FOOD_LOGGED_REQUEST = "home_food_logged"
        const val HOME_FOOD_LOGGED_MEAL_TYPE = "meal_type"

        private const val ARG_IMAGE_PATH = "image_path"
        private const val ARG_ANALYSIS_JSON = "analysis_json"
        private const val PROTEIN_TARGET_G = 60f
        private const val CARB_TARGET_G = 90f
        private const val FAT_TARGET_G = 40f

        fun newInstance(
            imagePath: String,
            analysisResult: FoodAnalysisResult? = null,
        ): FoodAnalysisResultFragment {
            return FoodAnalysisResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, imagePath)
                    analysisResult?.let {
                        putString(ARG_ANALYSIS_JSON, Json.encodeToString(it))
                    }
                }
            }
        }
    }
}
