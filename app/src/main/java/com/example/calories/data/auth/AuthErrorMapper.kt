package com.example.calories.data.auth

import java.io.IOException

object AuthErrorMapper {

    fun map(gotrueCode: String?, throwable: Throwable?): AuthError = when (gotrueCode) {
        "email_not_confirmed" -> AuthError.EmailNotConfirmed
        "invalid_credentials", "invalid_grant" -> AuthError.InvalidCredentials
        "user_already_exists", "email_exists" -> AuthError.EmailAlreadyRegistered
        "weak_password" -> AuthError.WeakPassword
        "over_email_send_rate_limit", "over_request_rate_limit" -> AuthError.RateLimited
        else -> if (throwable is IOException) {
            AuthError.Network
        } else {
            AuthError.Unknown(throwable?.message)
        }
    }
}
