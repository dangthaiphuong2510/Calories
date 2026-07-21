package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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
    private val supabase: SupabaseClient,
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
            try {
                supabase.auth.awaitInitialization()
                supabase.auth.resetPasswordForEmail(email)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.MessageRes(R.string.reset_email_sent))
                _navEvents.send(ForgotPasswordNavEvent.ToVerifyOtp(email))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(
                    UiEvent.Message(e.message ?: "Could not send reset email"),
                )
            }
        }
    }
}
