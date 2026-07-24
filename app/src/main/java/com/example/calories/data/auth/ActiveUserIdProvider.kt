package com.example.calories.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the active logged-in user id.
 * Matches [com.example.calories.ui.home.HomeViewModel] and repository session lookups.
 */
@Singleton
class ActiveUserIdProvider @Inject constructor(
    private val supabase: SupabaseClient,
) {
    fun get(): String? = supabase.auth.currentUserOrNull()?.id
}
