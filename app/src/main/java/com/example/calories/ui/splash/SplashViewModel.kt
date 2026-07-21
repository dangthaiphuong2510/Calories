package com.example.calories.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calories.data.auth.AuthDestination
import com.example.calories.data.auth.AuthNavigationResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SplashNavEvent {
    data object ToLogin : SplashNavEvent
    data object ToMain : SplashNavEvent
    data object ToOnboarding : SplashNavEvent
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authNavigationResolver: AuthNavigationResolver,
) : ViewModel() {

    private val _navEvents = Channel<SplashNavEvent>(Channel.BUFFERED)
    val navEvents = _navEvents.receiveAsFlow()

    init {
        viewModelScope.launch {
            val destination = authNavigationResolver.resolveDestination(requireRememberedSession = true)
            _navEvents.send(destination.toSplashNavEvent())
        }
    }

    private fun AuthDestination.toSplashNavEvent(): SplashNavEvent = when (this) {
        AuthDestination.LOGIN -> SplashNavEvent.ToLogin
        AuthDestination.MAIN -> SplashNavEvent.ToMain
        AuthDestination.ONBOARDING -> SplashNavEvent.ToOnboarding
    }
}
