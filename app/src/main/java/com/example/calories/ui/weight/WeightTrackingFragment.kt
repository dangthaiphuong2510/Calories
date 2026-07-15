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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

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
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun observeViewModel() {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
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
