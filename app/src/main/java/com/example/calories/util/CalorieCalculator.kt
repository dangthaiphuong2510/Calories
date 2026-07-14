package com.example.calories.util

import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import kotlin.math.roundToInt

object CalorieCalculator {

    fun calculateBmr(
        weightKg: Double,
        heightCm: Double,
        age: Int,
        gender: Gender,
    ): Double {
        val base = (10 * weightKg) + (6.25 * heightCm) - (5 * age)
        return when (gender) {
            Gender.MALE -> base + 5
            Gender.FEMALE -> base - 161
        }
    }

    fun calculateTdee(bmr: Double, activityLevel: ActivityLevel): Int {
        val multiplier = when (activityLevel) {
            ActivityLevel.SEDENTARY -> 1.2
            ActivityLevel.LIGHT -> 1.375
            ActivityLevel.MODERATE -> 1.55
            ActivityLevel.ACTIVE -> 1.725
            ActivityLevel.VERY_ACTIVE -> 1.9
        }
        return (bmr * multiplier).roundToInt()
    }

    fun calculateDailyCalories(tdee: Int, goalType: GoalType): Int {
        return when (goalType) {
            GoalType.LOSE_WEIGHT -> tdee - 500
            GoalType.GAIN_MUSCLE -> tdee + 300
            GoalType.MAINTAIN -> tdee
        }
    }
}
