package com.example.calories.model.enums

import kotlinx.serialization.Serializable

@Serializable
enum class GoalType {
    LOSE_WEIGHT,
    GAIN_MUSCLE,
    MAINTAIN,
}
