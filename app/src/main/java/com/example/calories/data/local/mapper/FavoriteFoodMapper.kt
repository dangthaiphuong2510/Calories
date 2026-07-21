package com.example.calories.data.local.mapper

import com.example.calories.data.local.entity.FavoriteFoodEntity
import com.example.calories.model.FoodDictionaryItem

fun FavoriteFoodEntity.toDictionaryItem(): FoodDictionaryItem {
    return FoodDictionaryItem(
        id = foodId,
        name = name,
        calories = calories,
        protein = protein,
        carb = carb,
        fat = fat,
    )
}

fun FoodDictionaryItem.toFavoriteEntity(userId: String): FavoriteFoodEntity {
    return FavoriteFoodEntity(
        userId = userId,
        foodId = id,
        name = name,
        calories = calories,
        protein = protein,
        carb = carb,
        fat = fat,
    )
}
