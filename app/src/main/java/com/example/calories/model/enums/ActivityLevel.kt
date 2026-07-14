package com.example.calories.model.enums

import kotlinx.serialization.Serializable

@Serializable
enum class ActivityLevel {
    SEDENTARY,
    LIGHT,
    MODERATE,
    ACTIVE,
    VERY_ACTIVE,
}
