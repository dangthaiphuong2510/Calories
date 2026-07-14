package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FridgeIngredient(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
)

@Serializable
data class FridgeIngredientInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    @SerialName("expiry_date") val expiryDate: String? = null,
)
