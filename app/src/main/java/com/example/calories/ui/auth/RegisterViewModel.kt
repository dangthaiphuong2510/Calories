package com.example.calories.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.R
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

data class RegisterUiState(
    val isLoading: Boolean = false,
)

sealed interface RegisterNavEvent {
    data object ToLogin : RegisterNavEvent
}

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _navEvents = Channel<RegisterNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = authRepository.registerWithEmail(name, email, password)) {
                is AuthResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(R.string.register_success_message))
                    _navEvents.send(RegisterNavEvent.ToLogin)
                }
                is AuthResult.Failure -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(UiEvent.MessageRes(result.error.toMessageRes()))
                }
            }
        }
    }
}
