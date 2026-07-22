package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.data.auth.AuthError
import com.example.calories.data.auth.AuthRepository
import com.example.calories.data.auth.AuthResult
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

data class ForgotPasswordUiState(
    val isLoading: Boolean = false,
)

sealed interface ForgotPasswordNavEvent {
    data class ToVerifyOtp(val email: String) : ForgotPasswordNavEvent
}

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<ForgotPasswordNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun sendRecoveryOtp(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.sendRecoveryEmail(email)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(R.string.reset_email_sent))
                    _navEvents.send(ForgotPasswordNavEvent.ToVerifyOtp(email))
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                }
            }
        }
    }
}
