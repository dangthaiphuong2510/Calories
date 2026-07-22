package com.example.calories.data.auth

sealed interface AuthError {
    data object InvalidCredentials : AuthError
    data object EmailNotConfirmed : AuthError
    data object EmailAlreadyRegistered : AuthError
    data object WeakPassword : AuthError
    data object RateLimited : AuthError
    data object Network : AuthError
    data object Cancelled : AuthError
    data class Unknown(val message: String?) : AuthError
}
