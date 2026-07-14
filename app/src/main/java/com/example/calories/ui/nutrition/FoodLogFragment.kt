package com.example.calories.ui.nutrition

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.DialogAddFoodBinding
import com.example.calories.databinding.FragmentFoodLogBinding
import com.example.calories.model.enums.MealType
import com.example.calories.ui.camera.FoodCameraFragment
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.nutrition.adapter.FoodEntryAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class FoodLogFragment : BaseFragment<FragmentFoodLogBinding>() {

    private val viewModel: FoodLogViewModel by viewModels()
    private val adapter = FoodEntryAdapter()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentFoodLogBinding = FragmentFoodLogBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvFoodEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFoodEntries.adapter = adapter
        binding.fabAddFood.setOnClickListener { showAddFoodDialog() }
        binding.fabScanFood.setOnClickListener { openCamera() }
        binding.btnHistory.setOnClickListener { openHistory() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.tvSelectedDate.text = state.dateLabel
            adapter.submitList(state.entries)
            val isEmpty = state.entries.isEmpty()
            binding.tvEmptyFood.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvFoodEntries.visibility = if (isEmpty) View.GONE else View.VISIBLE
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

    private fun openCamera() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostFragment, FoodCameraFragment())
            .addToBackStack("food_camera")
            .commit()
    }

    private fun openHistory() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.navHostFragment, FoodHistoryFragment())
            .addToBackStack("food_history")
            .commit()
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
}
