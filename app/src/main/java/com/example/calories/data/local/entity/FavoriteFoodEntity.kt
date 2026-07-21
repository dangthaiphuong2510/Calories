package com.example.calories.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "favorite_foods",
    primaryKeys = ["userId", "foodId"],
)
data class FavoriteFoodEntity(
    val userId: String,
    val foodId: String,
    val name: String,
    val calories: Long? = null,
    val protein: Double? = null,
    val carb: Double? = null,
    val fat: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
