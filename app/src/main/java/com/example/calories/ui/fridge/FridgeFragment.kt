package com.example.calories.ui.fridge

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.calories.R
import com.example.calories.databinding.DialogAddIngredientBinding
import com.example.calories.databinding.FragmentFridgeBinding
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.util.Calendar

@AndroidEntryPoint
class FridgeFragment : Fragment() {

    private var _binding: FragmentFridgeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FridgeViewModel by viewModels()
    private val adapter = FridgeIngredientAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentFridgeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.rvIngredients.layoutManager = LinearLayoutManager(requireContext())
        binding.rvIngredients.adapter = adapter
        binding.fabAddIngredient.setOnClickListener { showAddIngredientDialog() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            adapter.submitList(state.ingredients)
            val isEmpty = state.ingredients.isEmpty()
            binding.tvEmptyFridge.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.rvIngredients.visibility = if (isEmpty) View.GONE else View.VISIBLE
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

    private fun showAddIngredientDialog() {
        val dialogBinding = DialogAddIngredientBinding.inflate(layoutInflater)
        var selectedExpiry: String? = null

        dialogBinding.etExpiry.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    selectedExpiry = LocalDate.of(year, month + 1, dayOfMonth).toString()
                    dialogBinding.etExpiry.setText(selectedExpiry)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH),
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val name = dialogBinding.etIngredientName.text?.toString()?.trim().orEmpty()
            val quantity = dialogBinding.etQuantity.text?.toString()?.toDoubleOrNull()
            val unit = dialogBinding.etUnit.text?.toString()?.trim().orEmpty()
            if (name.isBlank() || quantity == null || unit.isBlank()) {
                Toast.makeText(requireContext(), R.string.error_fill_all_fields, Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            viewModel.addIngredient(name, quantity, unit, selectedExpiry)
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
