package com.example.calories.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val supabase: SupabaseClient,
) : ViewModel() {

    fun isLoggedIn(): Boolean = supabase.auth.currentUserOrNull() != null
}
