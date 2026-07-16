package com.example.calories.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentOnboardingGoalsBinding
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.GoalType
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingGoalsFragment : Fragment() {

    private var _binding: FragmentOnboardingGoalsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    private var didPrefill = false
    private var suppressWatcher = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingGoalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDropdowns()
        binding.etTargetWeight.addTextChangedListener(simpleWatcher {
            if (suppressWatcher) return@simpleWatcher
            viewModel.clearFieldError(OnboardingField.TARGET_WEIGHT)
            viewModel.updateTargetWeight(binding.etTargetWeight.text?.toString()?.toDoubleOrNull())
        })
        viewLifecycleOwner.collectLatestStarted(viewModel.formState) { state ->
            if (state.isPrefillReady && !didPrefill) {
                didPrefill = true
                suppressWatcher = true
                binding.actActivityLevel.setText(
                    activityLabels()[state.activityLevel.ordinal],
                    false,
                )
                binding.actGoalType.setText(goalLabels()[state.goalType.ordinal], false)
                binding.etTargetWeight.setText(state.targetWeight?.toString().orEmpty())
                suppressWatcher = false
            }
            binding.tilTargetWeight.error = state.targetWeightErrorRes?.let { getString(it) }
        }
    }

    private fun setupDropdowns() {
        val activityLabels = activityLabels()
        binding.actActivityLevel.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, activityLabels),
        )
        binding.actActivityLevel.setText(activityLabels[ActivityLevel.MODERATE.ordinal], false)
        binding.actActivityLevel.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateActivityLevel(ActivityLevel.entries[position])
        }

        val goalLabels = goalLabels()
        binding.actGoalType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, goalLabels),
        )
        binding.actGoalType.setText(goalLabels[GoalType.MAINTAIN.ordinal], false)
        binding.actGoalType.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateGoalType(GoalType.entries[position])
        }
    }

    private fun activityLabels() = listOf(
        getString(R.string.activity_sedentary),
        getString(R.string.activity_light),
        getString(R.string.activity_moderate),
        getString(R.string.activity_active),
        getString(R.string.activity_very_active),
    )

    private fun goalLabels() = listOf(
        getString(R.string.goal_lose_weight),
        getString(R.string.goal_gain_muscle),
        getString(R.string.goal_maintain),
    )

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
