package com.example.calories.data.auth

data class AuthUser(
    val id: String,
    val email: String?,
    val isEmailConfirmed: Boolean,
    val linkedProviders: List<String>,
) {
    val hasEmailPasswordIdentity: Boolean get() = linkedProviders.any { it == "email" }
    val hasGoogleIdentity: Boolean get() = linkedProviders.any { it == "google" }
    val isGoogleOnly: Boolean get() = hasGoogleIdentity && !hasEmailPasswordIdentity
}
