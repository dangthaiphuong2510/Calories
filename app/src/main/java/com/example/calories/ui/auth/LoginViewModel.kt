package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.repository.UserGoalsRepository
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
)

sealed interface LoginNavEvent {
    data object ToMain : LoginNavEvent
    data object ToOnboarding : LoginNavEvent
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val userGoalsRepository: UserGoalsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoginUiState(isLoggedIn = supabase.auth.currentUserOrNull() != null),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<LoginNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    init {
        if (_uiState.value.isLoggedIn) {
            resolveDestination()
        }
    }

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
                resolveDestination()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.Message(e.message ?: "Login failed"))
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            try {
                supabase.auth.resetPasswordForEmail(email)
                _events.send(UiEvent.Message("Password reset email sent"))
            } catch (e: Exception) {
                _events.send(UiEvent.Message(e.message ?: "Could not send reset email"))
            }
        }
    }

    private fun resolveDestination() {
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.update { it.copy(isLoading = false, isLoggedIn = false) }
                return@launch
            }
            val hasGoal = try {
                userGoalsRepository.refresh(userId)
                userGoalsRepository.observeGoal(userId).first() != null
            } catch (_: Exception) {
                userGoalsRepository.observeGoal(userId).first() != null
            }
            _navEvents.send(
                if (hasGoal) LoginNavEvent.ToMain else LoginNavEvent.ToOnboarding,
            )
        }
    }
}
