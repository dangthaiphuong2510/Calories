package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WaterEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("amount_ml") val amountMl: Int,
    @SerialName("created_at") val createdAt: String,
)
