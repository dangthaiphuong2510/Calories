package com.example.calories.ui.auth

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.calories.BuildConfig
import com.example.calories.R
import com.example.calories.databinding.ActivityLoginBinding
import com.example.calories.ui.MainActivity
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import com.example.calories.ui.onboarding.OnboardingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            if (!validate(email, password)) return@setOnClickListener
            viewModel.login(email, password)
        }
        binding.btnGoogleLogin.setOnClickListener { startGoogleSignIn() }
        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.error = getString(R.string.error_email_invalid)
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            viewModel.resetPassword(email)
        }
        binding.tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun startGoogleSignIn() {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            Toast.makeText(this, R.string.google_web_client_id_missing, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            try {
                val result = GoogleSignInHelper.signIn(
                    activity = this@LoginActivity,
                    webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
                )
                viewModel.loginWithGoogle(result.idToken, result.rawNonce)
            } catch (_: GoogleSignInException.Cancelled) {
                Toast.makeText(this@LoginActivity, R.string.google_sign_in_cancelled, Toast.LENGTH_SHORT).show()
            } catch (_: GoogleSignInException.NotConfigured) {
                Toast.makeText(this@LoginActivity, R.string.google_web_client_id_missing, Toast.LENGTH_LONG).show()
            } catch (e: GoogleSignInException.Failed) {
                Toast.makeText(
                    this@LoginActivity,
                    e.message?.takeIf { it.isNotBlank() && it != "credential_failed" }
                        ?: getString(R.string.google_sign_in_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@LoginActivity,
                    e.message ?: getString(R.string.google_sign_in_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.btnLogin.isEnabled = !state.isLoading
            binding.btnGoogleLogin.isEnabled = !state.isLoading
            binding.btnLogin.text = if (state.isLoading) "..." else getString(R.string.login)
        }
        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes -> Toast.makeText(this, event.resId, Toast.LENGTH_LONG).show()
            }
        }
        collectLatestStarted(viewModel.navEvents) { event ->
            val destination = when (event) {
                LoginNavEvent.ToMain -> MainActivity::class.java
                LoginNavEvent.ToOnboarding -> OnboardingActivity::class.java
            }
            startActivity(
                Intent(this, destination).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
            finish()
        }
    }

    private fun validate(email: String, password: String): Boolean {
        var isValid = true
        binding.tilEmail.error = null
        binding.tilPassword.error = null

        if (email.isBlank()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            isValid = false
        }

        if (password.isBlank()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            isValid = false
        }
        return isValid
    }
}
