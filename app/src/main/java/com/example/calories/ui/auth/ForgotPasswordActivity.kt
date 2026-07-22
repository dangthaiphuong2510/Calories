package com.example.calories.ui.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import com.example.calories.R
import com.example.calories.databinding.ActivityForgotPasswordBinding
import com.example.calories.ui.common.BaseActivity
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ForgotPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val viewModel: ForgotPasswordViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        intent.getStringExtra(EXTRA_EMAIL)?.let { email ->
            binding.etEmail.setText(email)
        }

        setupListeners()
        observeViewModel()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri: Uri = intent?.data ?: return

        if (uri.scheme == "calories" && uri.host == "reset-password") {
            val fragment = uri.fragment
            if (!fragment.isNullOrBlank()) {
                val accessToken = fragment.split("&")
                    .firstOrNull { it.startsWith("access_token=") }
                    ?.substringAfter("access_token=")

                if (!accessToken.isNullOrEmpty()) {
                    Log.d("ForgotPasswordActivity", "Access Token to Deep Link: $accessToken")

                    startActivity(
                        Intent(this, VerifyOtpResetPasswordActivity::class.java).apply {
                            putExtra(VerifyOtpResetPasswordActivity.EXTRA_ACCESS_TOKEN, accessToken)
                        }
                    )
                    finish()
                }
            }
        }
    }

    private fun setupListeners() {
        binding.tvBackToLogin.setOnClickListener { finish() }
        binding.btnSendOtp.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (!validateEmail(email)) return@setOnClickListener
            viewModel.sendRecoveryOtp(email)
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.btnSendOtp.isEnabled = !state.isLoading
            binding.btnSendOtp.text = if (state.isLoading) {
                "..."
            } else {
                getString(R.string.send_reset_code)
            }
        }
        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes -> Toast.makeText(this, event.resId, Toast.LENGTH_LONG).show()
            }
        }
        collectLatestStarted(viewModel.navEvents) { event ->
            when (event) {
                is ForgotPasswordNavEvent.ToVerifyOtp -> {
                    startActivity(
                        Intent(this, VerifyOtpResetPasswordActivity::class.java).apply {
                            putExtra(VerifyOtpResetPasswordActivity.EXTRA_EMAIL, event.email)
                        },
                    )
                    finish()
                }
            }
        }
    }

    private fun validateEmail(email: String): Boolean {
        binding.tilEmail.error = null
        return when {
            email.isBlank() -> {
                binding.tilEmail.error = getString(R.string.error_email_required)
                false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = getString(R.string.error_email_invalid)
                false
            }
            else -> true
        }
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}