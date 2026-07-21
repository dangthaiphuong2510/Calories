package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
import com.example.calories.ui.common.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VerifyOtpResetPasswordUiState(
    val isLoading: Boolean = false,
)

sealed interface VerifyOtpResetPasswordNavEvent {
    data object ToLogin : VerifyOtpResetPasswordNavEvent
}

@HiltViewModel
class VerifyOtpResetPasswordViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyOtpResetPasswordUiState())
    val uiState: StateFlow<VerifyOtpResetPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<VerifyOtpResetPasswordNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun verifyOtpAndUpdatePassword(email: String, otpCode: String, newPassword: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                supabase.auth.awaitInitialization()
                supabase.auth.verifyEmailOtp(
                    type = OtpType.Email.RECOVERY,
                    email = email,
                    token = otpCode,
                )
                supabase.auth.updateUser {
                    password = newPassword
                }
                runCatching { supabase.auth.signOut() }
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.MessageRes(R.string.password_updated_successfully))
                _navEvents.send(VerifyOtpResetPasswordNavEvent.ToLogin)
            } catch (e: AuthRestException) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.MessageRes(R.string.invalid_or_expired_otp))
            } catch (e: RestException) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.MessageRes(R.string.invalid_or_expired_otp))
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(UiEvent.Message(e.message ?: "Could not update password"))
            }
        }
    }
}
