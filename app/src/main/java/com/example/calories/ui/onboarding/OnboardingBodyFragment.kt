package com.example.calories.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calories.databinding.FragmentOnboardingBodyBinding
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingBodyFragment : Fragment() {

    private var _binding: FragmentOnboardingBodyBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    private var didPrefill = false
    private var suppressWatcher = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingBodyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etHeight.addTextChangedListener(simpleWatcher {
            if (suppressWatcher) return@simpleWatcher
            viewModel.clearFieldError(OnboardingField.HEIGHT)
            viewModel.updateHeight(binding.etHeight.text?.toString()?.toDoubleOrNull())
        })
        binding.etCurrentWeight.addTextChangedListener(simpleWatcher {
            if (suppressWatcher) return@simpleWatcher
            viewModel.clearFieldError(OnboardingField.CURRENT_WEIGHT)
            viewModel.updateCurrentWeight(binding.etCurrentWeight.text?.toString()?.toDoubleOrNull())
        })
        viewLifecycleOwner.collectLatestStarted(viewModel.formState) { state ->
            if (state.isPrefillReady && !didPrefill) {
                didPrefill = true
                suppressWatcher = true
                binding.etHeight.setText(state.heightCm?.toString().orEmpty())
                binding.etCurrentWeight.setText(state.currentWeight?.toString().orEmpty())
                suppressWatcher = false
            }
            binding.tilHeight.error = state.heightErrorRes?.let { getString(it) }
            binding.tilCurrentWeight.error = state.currentWeightErrorRes?.let { getString(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
