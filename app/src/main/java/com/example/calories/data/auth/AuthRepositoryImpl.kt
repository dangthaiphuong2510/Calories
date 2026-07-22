package com.example.calories.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {

    override suspend fun registerWithEmail(
        name: String,
        email: String,
        password: String,
    ): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("full_name", name) }
        }
        if (supabase.auth.currentSessionOrNull() != null) {
            runCatching { supabase.auth.signOut() }
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String,
    ): AuthResult<AuthUser> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        requireCurrentUser()
    }

    override suspend fun signInWithGoogle(
        idToken: String,
        rawNonce: String,
    ): AuthResult<AuthUser> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
        requireCurrentUser()
    }

    override suspend fun resendConfirmationEmail(email: String): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override suspend fun sendRecoveryEmail(email: String): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = AuthRepository.RECOVERY_REDIRECT_URL,
        )
    }

    override suspend fun updatePasswordWithToken(
        accessToken: String,
        newPassword: String,
    ): AuthResult<Unit> = runAuth {
        supabase.auth.awaitInitialization()
        supabase.auth.importAuthToken(accessToken, retrieveUser = true)
        supabase.auth.updateUser {
            password = newPassword
        }
        runCatching { supabase.auth.signOut() }
    }

    override suspend fun currentUser(): AuthUser? {
        supabase.auth.awaitInitialization()
        return currentUserSnapshotOrNull()
    }

    override suspend fun signOut() {
        runCatching {
            supabase.auth.awaitInitialization()
            supabase.auth.signOut()
        }
    }

    private suspend fun requireCurrentUser(): AuthUser =
        currentUserSnapshotOrNull() ?: throw IllegalStateException("no_session_after_auth")

    private suspend fun currentUserSnapshotOrNull(): AuthUser? {
        val user = supabase.auth.currentUserOrNull() ?: return null
        val providers = (supabase.auth.currentIdentitiesOrNull() ?: emptyList())
            .map { it.provider }
        return AuthUser(
            id = user.id,
            email = user.email,
            isEmailConfirmed = user.emailConfirmedAt != null,
            linkedProviders = providers,
        )
    }

    private suspend inline fun <T> runAuth(block: suspend () -> T): AuthResult<T> = try {
        AuthResult.Success(block())
    } catch (e: RestException) {
        AuthResult.Failure(AuthErrorMapper.map(e.error, e))
    } catch (e: IOException) {
        AuthResult.Failure(AuthError.Network)
    } catch (e: Exception) {
        AuthResult.Failure(AuthError.Unknown(e.message))
    }
}
