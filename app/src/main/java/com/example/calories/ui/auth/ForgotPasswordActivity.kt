package com.example.calories.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.calories.R
import com.example.calories.databinding.ActivityForgotPasswordBinding
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {

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
