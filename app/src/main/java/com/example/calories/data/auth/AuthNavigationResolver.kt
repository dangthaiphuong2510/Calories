package com.example.calories.data.auth

import com.example.calories.data.preferences.AuthDataStore
import com.example.calories.data.repository.UserGoalsRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class AuthDestination {
    LOGIN,
    MAIN,
    ONBOARDING,
}

@Singleton
class AuthNavigationResolver @Inject constructor(
    private val supabase: SupabaseClient,
    private val userGoalsRepository: UserGoalsRepository,
    private val authDataStore: AuthDataStore,
) {

    suspend fun resolveDestination(requireRememberedSession: Boolean = false): AuthDestination {
        supabase.auth.awaitInitialization()

        if (requireRememberedSession && !authDataStore.isLoggedInFlow.first()) {
            return AuthDestination.LOGIN
        }

        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            authDataStore.clearLoginState()
            return AuthDestination.LOGIN
        }

        val hasGoal = try {
            userGoalsRepository.refresh(userId)
            userGoalsRepository.observeGoal(userId).first() != null
        } catch (_: Exception) {
            userGoalsRepository.observeGoal(userId).first() != null
        }

        return if (hasGoal) AuthDestination.MAIN else AuthDestination.ONBOARDING
    }

    suspend fun markAuthenticated() {
        authDataStore.setLoggedIn(true)
    }
}
