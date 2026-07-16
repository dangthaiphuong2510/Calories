package com.example.calories.ui.weight

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.DialogAddWeightBinding
import com.example.calories.databinding.FragmentWeightTrackingBinding
import com.example.calories.ui.common.BaseFragment
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneOffset

@AndroidEntryPoint
class WeightTrackingFragment : BaseFragment<FragmentWeightTrackingBinding>() {

    private val viewModel: WeightTrackingViewModel by viewModels()
    private val adapter = WeightEntryAdapter()

    override fun inflateBinding(
        inflater: LayoutInflater,
        container: ViewGroup?,
    ): FragmentWeightTrackingBinding =
        FragmentWeightTrackingBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvWeightEntries.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWeightEntries.adapter = adapter
        binding.fabLogWeight.setOnClickListener { showLogWeightDialog() }
        setupNutritionControls()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun setupNutritionControls() {
        binding.toggleNutritionPeriod.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val period = when (checkedId) {
                R.id.btnNutritionDay -> NutritionPeriod.DAY
                else -> NutritionPeriod.WEEK
            }
            viewModel.setNutritionPeriod(period)
        }
        binding.btnNutritionDate.setOnClickListener { showNutritionDatePicker() }
    }

    private fun observeViewModel() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val burnedColor = ContextCompat.getColor(requireContext(), R.color.macro_fat)
        val proteinColor = ContextCompat.getColor(requireContext(), R.color.macro_protein)
        val carbColor = ContextCompat.getColor(requireContext(), R.color.macro_carb)
        val fatColor = ContextCompat.getColor(requireContext(), R.color.macro_fat)

        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.tvCurrentWeight.text = state.currentWeightKg?.let {
                getString(R.string.weight_kg_format, it)
            } ?: "— kg"
            binding.tvTargetWeight.text = state.targetWeightKg?.let {
                getString(R.string.weight_kg_format, it)
            } ?: "— kg"
            adapter.submitList(state.entries)
            WeightChartHelper.bind(binding.weightChart, state.entries, primaryColor)
            binding.tvEmptyWeight.visibility =
                if (state.entries.isEmpty()) View.VISIBLE else View.GONE

            syncNutritionToggle(state.nutritionPeriod)
            binding.tvNutritionPeriodLabel.text = state.periodLabel
            binding.tvMacroSectionTitle.text = when (state.nutritionPeriod) {
                NutritionPeriod.DAY -> getString(R.string.macro_distribution)
                NutritionPeriod.WEEK -> getString(R.string.weekly_macro_distribution)
            }

            CalorieTrendChartHelper.bind(
                chart = binding.calorieTrendChart,
                points = state.calorieTrend,
                period = state.nutritionPeriod,
                consumedColor = primaryColor,
                burnedColor = burnedColor,
                consumedLabel = getString(R.string.legend_calories_consumed),
                burnedLabel = getString(R.string.legend_calories_burned),
                emptyMessage = getString(R.string.chart_no_calorie_data),
            )

            val distribution = state.macroDistribution.copy(
                centerLabel = when (state.nutritionPeriod) {
                    NutritionPeriod.DAY -> getString(R.string.macro_center_day)
                    NutritionPeriod.WEEK -> getString(R.string.macro_center_week)
                },
            )
            MacroDistributionChartHelper.bind(
                chart = binding.macroDistributionChart,
                distribution = distribution,
                proteinColor = proteinColor,
                carbColor = carbColor,
                fatColor = fatColor,
                emptyMessage = getString(R.string.macro_legend_empty),
            )
            bindMacroLegend(distribution, proteinColor, carbColor, fatColor)
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

    private fun syncNutritionToggle(period: NutritionPeriod) {
        val targetId = when (period) {
            NutritionPeriod.DAY -> R.id.btnNutritionDay
            NutritionPeriod.WEEK -> R.id.btnNutritionWeek
        }
        if (binding.toggleNutritionPeriod.checkedButtonId != targetId) {
            binding.toggleNutritionPeriod.check(targetId)
        }
    }

    private fun bindMacroLegend(
        distribution: MacroDistributionUi,
        proteinColor: Int,
        carbColor: Int,
        fatColor: Int,
    ) {
        if (!distribution.hasData) {
            binding.tvLegendProtein.text = getString(R.string.macro_legend_empty)
            binding.tvLegendProtein.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_hint),
            )
            binding.tvLegendCarb.visibility = View.GONE
            binding.tvLegendFat.visibility = View.GONE
            return
        }

        binding.tvLegendCarb.visibility = View.VISIBLE
        binding.tvLegendFat.visibility = View.VISIBLE

        binding.tvLegendProtein.text = getString(
            R.string.macro_legend_format,
            getString(R.string.protein),
            distribution.proteinPercent,
            distribution.proteinGrams,
        )
        binding.tvLegendProtein.setTextColor(proteinColor)

        binding.tvLegendCarb.text = getString(
            R.string.macro_legend_format,
            getString(R.string.carb),
            distribution.carbPercent,
            distribution.carbGrams,
        )
        binding.tvLegendCarb.setTextColor(carbColor)

        binding.tvLegendFat.text = getString(
            R.string.macro_legend_format,
            getString(R.string.fat),
            distribution.fatPercent,
            distribution.fatGrams,
        )
        binding.tvLegendFat.setTextColor(fatColor)
    }

    private fun showNutritionDatePicker() {
        val current = viewModel.selectedDate.value
        val selection = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val titleRes = when (viewModel.nutritionPeriod.value) {
            NutritionPeriod.DAY -> R.string.select_nutrition_date
            NutritionPeriod.WEEK -> R.string.select_nutrition_week
        }
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(titleRes)
            .setSelection(selection)
            .setTheme(R.style.ThemeOverlay_Calories_MaterialCalendar)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val selected = Instant.ofEpochMilli(millis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate()
            viewModel.selectNutritionDate(selected)
        }
        picker.show(parentFragmentManager, "nutrition_date_picker")
    }

    private fun showLogWeightDialog() {
        val dialogBinding = DialogAddWeightBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val weight = dialogBinding.etWeight.text?.toString()?.toDoubleOrNull()
            if (weight == null || weight <= 0) {
                Toast.makeText(requireContext(), R.string.error_fill_all_fields, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            viewModel.addWeight(weight)
            dialog.dismiss()
        }
        dialog.show()
    }
}
