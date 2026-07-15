package com.example.calories.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.calories.R
import com.example.calories.databinding.ActivityOnboardingBinding
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import com.example.calories.ui.MainActivity
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGenderToggle()
        setupDropdowns()
        setupInputWatchers()
        binding.btnContinue.setOnClickListener {
            viewModel.updateTargetWeight(
                binding.etTargetWeight.text?.toString()?.toDoubleOrNull(),
            )
            viewModel.saveGoals()
        }
        observeViewModel()
    }

    private fun setupGenderToggle() {
        binding.tgGender.check(R.id.btnMale)
        binding.tgGender.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.updateGender(
                if (checkedId == R.id.btnFemale) Gender.FEMALE else Gender.MALE,
            )
        }
    }

    private fun setupDropdowns() {
        val activityLabels = listOf(
            getString(R.string.activity_sedentary),
            getString(R.string.activity_light),
            getString(R.string.activity_moderate),
            getString(R.string.activity_active),
            getString(R.string.activity_very_active),
        )
        binding.actActivityLevel.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, activityLabels),
        )
        binding.actActivityLevel.setText(activityLabels[2], false)
        binding.actActivityLevel.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateActivityLevel(ActivityLevel.entries[position])
        }

        val goalLabels = listOf(
            getString(R.string.goal_lose_weight),
            getString(R.string.goal_gain_muscle),
            getString(R.string.goal_maintain),
        )
        binding.actGoalType.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, goalLabels),
        )
        binding.actGoalType.setText(goalLabels[2], false)
        binding.actGoalType.setOnItemClickListener { _, _, position, _ ->
            viewModel.updateGoalType(GoalType.entries[position])
        }
    }

    private fun setupInputWatchers() {
        binding.etAge.addTextChangedListener(simpleWatcher {
            viewModel.updateAge(binding.etAge.text?.toString()?.toIntOrNull())
        })
        binding.etHeight.addTextChangedListener(simpleWatcher {
            viewModel.updateHeight(binding.etHeight.text?.toString()?.toDoubleOrNull())
        })
        binding.etCurrentWeight.addTextChangedListener(simpleWatcher {
            viewModel.updateCurrentWeight(binding.etCurrentWeight.text?.toString()?.toDoubleOrNull())
        })
        binding.etTargetWeight.addTextChangedListener(simpleWatcher {
            viewModel.updateTargetWeight(binding.etTargetWeight.text?.toString()?.toDoubleOrNull())
        })
    }

    private var didApplyPrefill = false

    private fun observeViewModel() {
        collectLatestStarted(viewModel.formState) { state ->
            if (state.isPrefillReady && !didApplyPrefill && state.existingGoalId != null) {
                didApplyPrefill = true
                applyPrefill(state)
            }
            binding.tvTdee.text = if (state.tdee == 0) "—" else state.tdee.toString()
            binding.tvDailyCalories.text =
                if (state.dailyCalories == 0) "—" else state.dailyCalories.toString()
            binding.btnContinue.isEnabled = !state.isLoading
            binding.btnContinue.text =
                if (state.isLoading) "..." else getString(R.string.continue_btn)
        }
        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes -> Toast.makeText(this, event.resId, Toast.LENGTH_LONG).show()
            }
        }
        collectLatestStarted(viewModel.navEvents) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            finish()
        }
    }

    private fun applyPrefill(state: OnboardingFormState) {
        binding.tgGender.check(
            if (state.gender == Gender.FEMALE) R.id.btnFemale else R.id.btnMale,
        )
        binding.etAge.setText(state.age?.toString().orEmpty())
        binding.etHeight.setText(state.heightCm?.toString().orEmpty())
        binding.etCurrentWeight.setText(state.currentWeight?.toString().orEmpty())
        binding.etTargetWeight.setText(state.targetWeight?.toString().orEmpty())

        val activityLabels = listOf(
            getString(R.string.activity_sedentary),
            getString(R.string.activity_light),
            getString(R.string.activity_moderate),
            getString(R.string.activity_active),
            getString(R.string.activity_very_active),
        )
        binding.actActivityLevel.setText(
            activityLabels[state.activityLevel.ordinal],
            false,
        )

        val goalLabels = listOf(
            getString(R.string.goal_lose_weight),
            getString(R.string.goal_gain_muscle),
            getString(R.string.goal_maintain),
        )
        binding.actGoalType.setText(goalLabels[state.goalType.ordinal], false)
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: Editable?) = onChanged()
    }
}
