package com.example.calories.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.calories.R
import com.example.calories.databinding.ActivityRegisterBinding
import com.example.calories.ui.common.UiEvent
import com.example.calories.ui.common.collectLatestStarted
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel: RegisterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim().orEmpty()
            val email = binding.etEmail.text?.toString()?.trim().orEmpty()
            val password = binding.etPassword.text?.toString().orEmpty()
            val confirmPassword = binding.etConfirmPassword.text?.toString().orEmpty()
            if (!validate(name, email, password, confirmPassword)) return@setOnClickListener
            viewModel.register(name, email, password)
        }
        binding.tvGoToLogin.setOnClickListener { finish() }
    }

    private fun observeViewModel() {
        collectLatestStarted(viewModel.uiState) { state ->
            binding.btnRegister.isEnabled = !state.isLoading
            binding.btnRegister.text = if (state.isLoading) "..." else getString(R.string.sign_up)
        }
        collectLatestStarted(viewModel.events) { event ->
            when (event) {
                is UiEvent.Message -> Toast.makeText(this, event.text, Toast.LENGTH_LONG).show()
                is UiEvent.MessageRes -> Toast.makeText(this, event.resId, Toast.LENGTH_LONG).show()
            }
        }
        collectLatestStarted(viewModel.navEvents) {
            finish()
        }
    }

    private fun validate(
        name: String,
        email: String,
        password: String,
        confirmPassword: String,
    ): Boolean {
        var isValid = true
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        if (name.isBlank()) {
            binding.tilName.error = getString(R.string.error_name_required)
            isValid = false
        }
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
        } else if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            isValid = false
        }
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.error_password_mismatch)
            isValid = false
        }
        return isValid
    }
}
