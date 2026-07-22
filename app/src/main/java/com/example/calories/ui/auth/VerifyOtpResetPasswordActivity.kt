package com.example.calories.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import com.example.calories.ui.common.BaseActivity
import com.example.calories.R
import com.example.calories.databinding.ActivityVerifyOtpResetPasswordBinding
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class VerifyOtpResetPasswordActivity : BaseActivity() {

    private lateinit var binding: ActivityVerifyOtpResetPasswordBinding
    private val viewModel: VerifyOtpResetPasswordViewModel by viewModels()

    private val userEmail: String by lazy {
        intent.getStringExtra(EXTRA_EMAIL).orEmpty()
    }

    private val accessToken: String? by lazy {
        intent.getStringExtra(EXTRA_ACCESS_TOKEN)
    }

    private val isDeepLinkFlow: Boolean
        get() = !accessToken.isNullOrBlank()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!isDeepLinkFlow && userEmail.isBlank()) {
            finish()
            return
        }

        binding = ActivityVerifyOtpResetPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (isDeepLinkFlow) {
            binding.tilOtp.visibility = View.GONE
            binding.tvVerifySubtitle.text = getString(R.string.reset_password_deep_link_subtitle)
        } else {
            binding.tvVerifySubtitle.text = getString(R.string.verify_otp_subtitle, userEmail)
        }

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.tvBackToLogin.setOnClickListener { finish() }
        binding.btnUpdatePassword.setOnClickListener {
            val newPassword = binding.etNewPassword.text?.toString().orEmpty()
            val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
            if (!validate(newPassword, confirmPassword)) return@setOnClickListener

            if (isDeepLinkFlow) {
                viewModel.updatePasswordWithToken(accessToken!!, newPassword)
            } else {
                val otpCode = binding.etOtp.text?.toString()?.trim().orEmpty()
                if (!validateOtp(otpCode)) return@setOnClickListener
                viewModel.verifyOtpAndUpdatePassword(userEmail, otpCode, newPassword)
            }
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.btnUpdatePassword.isEnabled = !state.isLoading
            binding.btnUpdatePassword.text = if (state.isLoading) {
                "..."
            } else {
                getString(R.string.update_password)
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
                VerifyOtpResetPasswordNavEvent.ToLogin -> {
                    startActivity(
                        Intent(this, LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        },
                    )
                    finish()
                }
            }
        }
    }

    private fun validateOtp(otpCode: String): Boolean {
        binding.tilOtp.error = null
        return when {
            otpCode.isBlank() -> {
                binding.tilOtp.error = getString(R.string.error_otp_required)
                false
            }
            otpCode.length != 6 || !otpCode.all { it.isDigit() } -> {
                binding.tilOtp.error = getString(R.string.error_otp_invalid)
                false
            }
            else -> true
        }
    }

    private fun validate(newPassword: String, confirmPassword: String): Boolean {
        var isValid = true
        binding.tilNewPassword.error = null
        binding.tilConfirmPassword.error = null

        if (newPassword.isBlank()) {
            binding.tilNewPassword.error = getString(R.string.error_password_required)
            isValid = false
        } else if (newPassword.length < 6) {
            binding.tilNewPassword.error = getString(R.string.error_password_short)
            isValid = false
        }

        if (confirmPassword != newPassword) {
            binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            isValid = false
        }

        return isValid
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_ACCESS_TOKEN = "extra_access_token"
    }
}
