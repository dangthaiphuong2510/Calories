package com.example.calories.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import androidx.core.view.isInvisible
import androidx.viewpager2.widget.ViewPager2
import com.example.calories.R
import com.example.calories.databinding.ActivityOnboardingBinding
import com.example.calories.ui.MainActivity
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OnboardingActivity : BaseActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = OnboardingPagerAdapter.PAGE_COUNT - 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setCurrentPage(position)
                updateChrome(position)
            }
        })

        binding.btnBack.setOnClickListener {
            val previous = binding.viewPager.currentItem - 1
            if (previous >= 0) binding.viewPager.currentItem = previous
        }
        binding.btnContinue.setOnClickListener { onContinueClicked() }

        updateChrome(0)
        observeViewModel()
    }

    private fun onContinueClicked() {
        val page = binding.viewPager.currentItem
        if (page == OnboardingPagerAdapter.PAGE_PLAN) {
            viewModel.saveGoals()
            return
        }
        if (viewModel.validatePage(page)) {
            binding.viewPager.currentItem = page + 1
        }
    }

    private fun updateChrome(position: Int) {
        val step = position + 1
        binding.tvStepLabel.text = getString(
            R.string.onboarding_step_format,
            step,
            OnboardingPagerAdapter.PAGE_COUNT,
        )
        binding.progressSteps.setProgressCompat(step, true)
        binding.btnBack.isInvisible = position == OnboardingPagerAdapter.PAGE_BASIC
        val isLoading = viewModel.formState.value.isLoading
        binding.btnContinue.isEnabled = !isLoading
        binding.btnContinue.text = when {
            isLoading -> "..."
            position == OnboardingPagerAdapter.PAGE_PLAN -> getString(R.string.get_started)
            else -> getString(R.string.continue_btn)
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.formState) { state ->
            updateChrome(state.currentPage)
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
}
