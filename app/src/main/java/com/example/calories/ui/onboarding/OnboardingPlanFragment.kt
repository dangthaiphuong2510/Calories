package com.example.calories.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.calories.R
import com.example.calories.databinding.FragmentOnboardingPlanBinding
import com.example.calories.model.enums.GoalType
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingPlanFragment : Fragment() {

    private var _binding: FragmentOnboardingPlanBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentOnboardingPlanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.collectLatestStarted(viewModel.formState) { state ->
            binding.tvTdee.text = if (state.tdee == 0) "—" else state.tdee.toString()
            binding.tvDailyCalories.text =
                if (state.dailyCalories == 0) "—" else state.dailyCalories.toString()
            val messageRes = when (state.goalType) {
                GoalType.LOSE_WEIGHT -> R.string.onboarding_plan_lose
                GoalType.GAIN_MUSCLE -> R.string.onboarding_plan_gain
                GoalType.MAINTAIN -> R.string.onboarding_plan_maintain
            }
            binding.tvMotivational.text = getString(messageRes, state.dailyCalories)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.recalculate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
