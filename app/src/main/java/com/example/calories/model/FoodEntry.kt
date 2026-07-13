package com.example.calories.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bảng `food_entries` trên Supabase:
 * - id: uuid (primary key)
 * - user_id: uuid (references auth.users)
 * - name: text
 * - calories: integer
 * - created_at: timestamptz
 */
@Serializable
data class FoodEntry(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val calories: Int,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class FoodEntryInsert(
    @SerialName("user_id") val userId: String,
    val name: String,
    val calories: Int,
)
