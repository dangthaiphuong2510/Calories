package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fridge_ingredients")
data class FridgeIngredientEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val quantity: Double,
    val unit: String,
    val expiryDate: String? = null,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
