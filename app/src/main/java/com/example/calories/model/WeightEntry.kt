package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("recorded_at") val recordedAt: String,
)

@Serializable
data class WeightEntryInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("weight_kg") val weightKg: Double,
    @SerialName("recorded_at") val recordedAt: String,
)
