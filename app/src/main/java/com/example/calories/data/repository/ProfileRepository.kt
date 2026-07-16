package com.example.calories.data.repository

import com.example.calories.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(userId: String): Flow<Profile?>
    suspend fun upsertProfile(
        displayName: String? = null,
        avatarUrl: String? = null,
        updateAvatar: Boolean = false,
        gender: String? = null,
        birthDate: String? = null,
    ): Profile
    suspend fun fetchAndSync()
    suspend fun refresh(userId: String)
}
