package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.auth.AuthDestination
import com.example.calories.data.auth.AuthError
import com.example.calories.data.auth.AuthNavigationResolver
import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthResult
import com.example.calories.data.auth.AuthUser
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
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
    data class PromptResendConfirmation(val email: String) : LoginNavEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
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
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> completeAuthentication()
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    if (result.error == AuthError.EmailNotConfirmed) {
                        _events.send(UiEvent.MessageRes(R.string.auth_email_not_confirmed))
                        _navEvents.send(LoginNavEvent.PromptResendConfirmation(email))
                    } else {
                        _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                    }
                }
            }
        }
    }

    fun loginWithGoogle(idToken: String, rawNonce: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.signInWithGoogle(idToken, rawNonce)) {
                is AuthResult.Success -> {
                    maybeInformAboutLinking(result.data)
                    completeAuthentication()
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                }
            }
        }
    }

    fun resendConfirmation(email: String) {
        viewModelScope.launch {
            when (val result = authRepository.resendConfirmationEmail(email)) {
                is AuthResult.Success ->
                    _events.send(UiEvent.MessageRes(R.string.auth_confirmation_resent))
                is AuthResult.Failure ->
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
            }
        }
    }

    private suspend fun maybeInformAboutLinking(user: AuthUser) {
        if (user.hasGoogleIdentity && user.hasEmailPasswordIdentity) {
            _events.send(UiEvent.MessageRes(R.string.auth_google_linked_info))
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
