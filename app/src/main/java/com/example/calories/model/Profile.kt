package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val gender: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
)
