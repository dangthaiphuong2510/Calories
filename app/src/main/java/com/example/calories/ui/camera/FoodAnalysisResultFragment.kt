package com.example.calories.ui.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentFoodAnalysisResultBinding
import com.example.calories.model.FoodAnalysisResult
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FoodAnalysisResultFragment : BaseFragment<FragmentFoodAnalysisResultBinding>() {

    private val viewModel: FoodAnalysisResultViewModel by viewModels()
    private lateinit var mealLabels: List<String>

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFoodAnalysisResultBinding =
        FragmentFoodAnalysisResultBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val analysis = FoodAnalysisResult(
            name = requireArguments().getString(ARG_NAME).orEmpty(),
            calories = requireArguments().getInt(ARG_CALORIES),
            protein = requireArguments().getDouble(ARG_PROTEIN),
            carb = requireArguments().getDouble(ARG_CARB),
            fat = requireArguments().getDouble(ARG_FAT),
        )

        binding.etFoodName.setText(analysis.name)
        binding.etCalories.setText(analysis.calories.toString())
        binding.etProtein.setText(analysis.protein.toString())
        binding.etCarb.setText(analysis.carb.toString())
        binding.etFat.setText(analysis.fat.toString())

        mealLabels = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack),
        )
        binding.actMealType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mealLabels),
        )
        binding.actMealType.setText(mealLabels.last(), false)

        binding.btnRetake.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnSaveFood.setOnClickListener { saveFood() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.btnSaveFood.isEnabled = !state.isSaving
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(requireContext(), event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(requireContext(), event.resId, Toast.LENGTH_LONG).show()
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) {
            parentFragmentManager.popBackStack(
                "food_camera",
                FragmentManager.POP_BACK_STACK_INCLUSIVE,
            )
        }
    }

    private fun saveFood() {
        val name = binding.etFoodName.text?.toString()?.trim().orEmpty()
        val calories = binding.etCalories.text?.toString()?.toIntOrNull()
        if (name.isBlank() || calories == null) {
            Toast.makeText(requireContext(), R.string.error_fill_all_fields, Toast.LENGTH_SHORT)
                .show()
            return
        }
        val mealIndex = mealLabels.indexOf(binding.actMealType.text?.toString())
        viewModel.saveFood(
            name = name,
            calories = calories,
            protein = binding.etProtein.text?.toString()?.toDoubleOrNull() ?: 0.0,
            carb = binding.etCarb.text?.toString()?.toDoubleOrNull() ?: 0.0,
            fat = binding.etFat.text?.toString()?.toDoubleOrNull() ?: 0.0,
            mealType = MealType.entries.getOrElse(mealIndex) { MealType.SNACK },
        )
    }

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_CALORIES = "calories"
        private const val ARG_PROTEIN = "protein"
        private const val ARG_CARB = "carb"
        private const val ARG_FAT = "fat"

        fun newInstance(result: FoodAnalysisResult): FoodAnalysisResultFragment {
            return FoodAnalysisResultFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NAME, result.name)
                    putInt(ARG_CALORIES, result.calories)
                    putDouble(ARG_PROTEIN, result.protein)
                    putDouble(ARG_CARB, result.carb)
                    putDouble(ARG_FAT, result.fat)
                }
            }
        }
    }
}
