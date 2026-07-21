package com.example.calories.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.calories.CaloriesApplication
import com.example.calories.databinding.ActivitySplashBinding
import com.example.calories.ui.MainActivity
import com.example.calories.ui.auth.LoginActivity
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        (application as? CaloriesApplication)?.appOpenAdManager?.loadAd(this)

        collectLatestStarted(viewModel.navEvents) { event ->
            val destination = when (event) {
                SplashNavEvent.ToLogin -> LoginActivity::class.java
                SplashNavEvent.ToMain -> MainActivity::class.java
                SplashNavEvent.ToOnboarding -> OnboardingActivity::class.java
            }

            startActivity(
                Intent(this, destination).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }
    }
}