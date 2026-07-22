package com.example.calories.data.auth

interface AuthRepository {

    /** Registers an email/password user. Success does not imply a session — email confirmation is required. */
    suspend fun registerWithEmail(name: String, email: String, password: String): AuthResult<Unit>

    /** Signs in with email/password. Fails with [AuthError.EmailNotConfirmed] if the email is unverified. */
    suspend fun signInWithEmail(email: String, password: String): AuthResult<AuthUser>

    /**
     * Signs in with a Google ID token. Supabase Automatic Linking merges this identity into an
     * existing user with the same verified email (no duplicate account is created).
     */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String): AuthResult<AuthUser>

    /** Re-sends the signup confirmation email for an unconfirmed account. */
    suspend fun resendConfirmationEmail(email: String): AuthResult<Unit>

    /** Sends a password recovery email with a deep-link redirect to [RECOVERY_REDIRECT_URL]. */
    suspend fun sendRecoveryEmail(email: String): AuthResult<Unit>

    /**
     * Updates the user's password using a recovery access token from the reset-password deep link.
     * Imports the token as a session, updates the password, then signs out.
     */
    suspend fun updatePasswordWithToken(accessToken: String, newPassword: String): AuthResult<Unit>

    /** Returns the currently authenticated user snapshot, or null if there is no session. */
    suspend fun currentUser(): AuthUser?

    suspend fun signOut()

    companion object {
        const val RECOVERY_REDIRECT_URL = "calories://reset-password"
    }
}
