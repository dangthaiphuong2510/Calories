package com.example.calories.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.calories.databinding.FragmentProfileBinding
import com.example.calories.ui.auth.LoginActivity
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnEditGoals.setOnClickListener {
            startActivity(Intent(requireContext(), OnboardingActivity::class.java))
        }
        binding.btnSignOut.setOnClickListener { viewModel.signOut() }
        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.collectLatestStarted(viewModel.uiState) { state ->
            binding.tvUserName.text = state.userName
            binding.tvUserEmail.text = state.userEmail
            val goal = state.goal
            if (goal == null) {
                binding.tvDailyCalories.text = "—"
                binding.tvGoalType.text = "—"
                binding.tvActivityLevel.text = "—"
            } else {
                binding.tvDailyCalories.text = goal.dailyCalories.toString()
                binding.tvGoalType.text = getString(viewModel.goalTypeLabelRes(goal.goalType))
                binding.tvActivityLevel.text =
                    getString(viewModel.activityLevelLabelRes(goal.activityLevel))
            }
        }
        viewLifecycleOwner.collectLatestStarted(viewModel.navEvents) {
            startActivity(
                Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
