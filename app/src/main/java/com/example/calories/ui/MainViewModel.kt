package com.example.calories.ui

import androidx.lifecycle.ViewModel
import com.example.calories.data.auth.AuthDestination
import com.example.calories.data.auth.AuthNavigationResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authNavigationResolver: AuthNavigationResolver,
) : ViewModel() {

    suspend fun shouldAllowAccess(): Boolean {
        return authNavigationResolver.resolveDestination(requireRememberedSession = true) !=
            AuthDestination.LOGIN
    }
}
