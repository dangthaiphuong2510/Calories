package com.example.calories.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.DialogAddFoodBinding
import com.example.calories.databinding.FragmentHomeBinding
import com.example.calories.model.enums.MealType
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.nutrition.adapter.FoodEntryAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()
    private val adapter = FoodEntryAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvRecentMeals.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecentMeals.adapter = adapter
        binding.fabAddFood.setOnClickListener { showAddFoodDialog() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.tvDate.text = state.dateLabel
            binding.tvCaloriesEaten.text = state.caloriesEaten.toString()
            binding.tvCaloriesGoal.text = getString(R.string.kcal_goal_format, state.calorieGoal)
            binding.tvCaloriesRemaining.text =
                getString(R.string.kcal_remaining_format, state.caloriesRemaining)
            binding.progressCalories.progress = state.progressPercent
            binding.tvProtein.text = getString(R.string.macro_grams_format, state.protein)
            binding.tvCarb.text = getString(R.string.macro_grams_format, state.carb)
            binding.tvFat.text = getString(R.string.macro_grams_format, state.fat)
            adapter.submitList(state.recentMeals)
            binding.tvEmptyMeals.visibility =
                if (state.recentMeals.isEmpty()) View.VISIBLE else View.GONE
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message ->
                    Toast.makeText(requireContext(), event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes ->
                    Toast.makeText(requireContext(), event.resId, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showAddFoodDialog() {
        val dialogBinding = DialogAddFoodBinding.inflate(layoutInflater)
        val mealLabels = listOf(
            getString(R.string.meal_breakfast),
            getString(R.string.meal_lunch),
            getString(R.string.meal_dinner),
            getString(R.string.meal_snack),
        )
        dialogBinding.actMealType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mealLabels),
        )
        dialogBinding.actMealType.setText(mealLabels.last(), false)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etFoodName.text?.toString()?.trim().orEmpty()
            val calories = dialogBinding.etCalories.text?.toString()?.toIntOrNull()
            if (name.isBlank() || calories == null) {
                Toast.makeText(requireContext(), R.string.error_fill_all_fields, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            val mealIndex = mealLabels.indexOf(dialogBinding.actMealType.text?.toString())
            viewModel.addFood(
                name = name,
                calories = calories,
                protein = dialogBinding.etProtein.text?.toString()?.toDoubleOrNull() ?: 0.0,
                carb = dialogBinding.etCarb.text?.toString()?.toDoubleOrNull() ?: 0.0,
                fat = dialogBinding.etFat.text?.toString()?.toDoubleOrNull() ?: 0.0,
                mealType = MealType.entries.getOrElse(mealIndex) { MealType.SNACK },
            )
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
