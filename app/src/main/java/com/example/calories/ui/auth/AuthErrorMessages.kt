package com.example.calories.ui.auth

import androidx.annotation.StringRes
import com.example.calories.R
import com.example.calories.data.auth.AuthError

@StringRes
fun AuthError.toMessageRes(): Int = when (this) {
    AuthError.EmailNotConfirmed -> R.string.auth_email_not_confirmed
    AuthError.InvalidCredentials -> R.string.auth_invalid_credentials
    AuthError.EmailAlreadyRegistered -> R.string.auth_email_already_registered
    AuthError.WeakPassword -> R.string.auth_weak_password
    AuthError.RateLimited -> R.string.auth_rate_limited
    AuthError.Network -> R.string.auth_network_error
    AuthError.Cancelled -> R.string.auth_generic_error
    is AuthError.Unknown -> R.string.auth_generic_error
}
