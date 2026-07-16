package com.example.calories.ui.auth

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.security.MessageDigest
import java.util.UUID

data class GoogleIdTokenResult(
    val idToken: String,
    val rawNonce: String,
)

sealed class GoogleSignInException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Cancelled : GoogleSignInException("cancelled")
    class Failed(message: String, cause: Throwable? = null) : GoogleSignInException(message, cause)
    class NotConfigured : GoogleSignInException("missing_web_client_id")
}

object GoogleSignInHelper {

    suspend fun signIn(
        activity: Activity,
        webClientId: String,
    ): GoogleIdTokenResult {
        if (webClientId.isBlank()) throw GoogleSignInException.NotConfigured()

        val credentialManager = CredentialManager.create(activity)
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = sha256(rawNonce)

        return try {
            requestGoogleIdToken(
                activity = activity,
                credentialManager = credentialManager,
                webClientId = webClientId,
                hashedNonce = hashedNonce,
                rawNonce = rawNonce,
                filterAuthorized = true,
            )
        } catch (e: NoCredentialException) {
            try {
                requestGoogleIdToken(
                    activity = activity,
                    credentialManager = credentialManager,
                    webClientId = webClientId,
                    hashedNonce = hashedNonce,
                    rawNonce = rawNonce,
                    filterAuthorized = false,
                )
            } catch (_: GetCredentialException) {
                requestSignInButtonToken(
                    activity = activity,
                    credentialManager = credentialManager,
                    webClientId = webClientId,
                    hashedNonce = hashedNonce,
                    rawNonce = rawNonce,
                )
            }
        } catch (_: GetCredentialCancellationException) {
            throw GoogleSignInException.Cancelled()
        } catch (_: GetCredentialException) {
            requestSignInButtonToken(
                activity = activity,
                credentialManager = credentialManager,
                webClientId = webClientId,
                hashedNonce = hashedNonce,
                rawNonce = rawNonce,
            )
        }
    }

    private suspend fun requestGoogleIdToken(
        activity: Activity,
        credentialManager: CredentialManager,
        webClientId: String,
        hashedNonce: String,
        rawNonce: String,
        filterAuthorized: Boolean,
    ): GoogleIdTokenResult {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterAuthorized)
            .setServerClientId(webClientId)
            .setNonce(hashedNonce)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            request = request,
            context = activity,
        )
        return parseIdToken(result.credential, rawNonce)
    }

    private suspend fun requestSignInButtonToken(
        activity: Activity,
        credentialManager: CredentialManager,
        webClientId: String,
        hashedNonce: String,
        rawNonce: String,
    ): GoogleIdTokenResult {
        return try {
            val signInOption = GetSignInWithGoogleOption.Builder(webClientId)
                .setNonce(hashedNonce)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activity,
            )
            parseIdToken(result.credential, rawNonce)
        } catch (e: GetCredentialCancellationException) {
            throw GoogleSignInException.Cancelled()
        } catch (e: GetCredentialException) {
            throw GoogleSignInException.Failed(e.message ?: "credential_failed", e)
        }
    }

    private fun parseIdToken(
        credential: androidx.credentials.Credential,
        rawNonce: String,
    ): GoogleIdTokenResult {
        val custom = credential as? CustomCredential
            ?: throw GoogleSignInException.Failed("unexpected_credential")
        if (custom.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleSignInException.Failed("unexpected_credential_type")
        }
        return try {
            val googleCredential = GoogleIdTokenCredential.createFrom(custom.data)
            GoogleIdTokenResult(
                idToken = googleCredential.idToken,
                rawNonce = rawNonce,
            )
        } catch (e: GoogleIdTokenParsingException) {
            throw GoogleSignInException.Failed("invalid_id_token", e)
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
