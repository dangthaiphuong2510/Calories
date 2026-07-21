package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.auth.AuthDestination
import com.example.calories.data.auth.AuthNavigationResolver
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
)

sealed interface LoginNavEvent {
    data object ToMain : LoginNavEvent
    data object ToOnboarding : LoginNavEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val authNavigationResolver: AuthNavigationResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<LoginNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                supabase.auth.awaitInitialization()
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                if (supabase.auth.currentUserOrNull() == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.Message("Login failed. Check email/password or confirm your email."))
                    return@launch
                }
                completeAuthentication()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.Message(e.message ?: "Login failed"))
            }
        }
    }

    fun loginWithGoogle(idToken: String, rawNonce: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                supabase.auth.awaitInitialization()
                supabase.auth.signInWith(IDToken) {
                    this.idToken = idToken
                    provider = Google
                    nonce = rawNonce
                }
                if (supabase.auth.currentUserOrNull() == null) {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(com.example.calories.R.string.google_sign_in_failed))
                    return@launch
                }
                completeAuthentication()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(
                    UiEvent.Message(e.message ?: "Google sign-in failed"),
                )
            }
        }
    }

    private suspend fun completeAuthentication() {
        authNavigationResolver.markAuthenticated()
        val destination = authNavigationResolver.resolveDestination()
        _uiState.update { it.copy(isLoading = false) }
        _navEvents.send(
            when (destination) {
                AuthDestination.MAIN -> LoginNavEvent.ToMain
                AuthDestination.ONBOARDING -> LoginNavEvent.ToOnboarding
                AuthDestination.LOGIN -> LoginNavEvent.ToMain
            },
        )
    }
}
