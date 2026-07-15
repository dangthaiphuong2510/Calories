package com.example.calories.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val mealType: String,
    val servingGrams: Double = 100.0,
    val createdAt: String,
    val isDirty: Boolean = false,
    val syncedAt: Long? = null,
)
