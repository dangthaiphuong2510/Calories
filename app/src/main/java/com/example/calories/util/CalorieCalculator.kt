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

    data class MacroTargets(
        val proteinGrams: Double,
        val carbsGrams: Double,
        val fatGrams: Double,
        val fiberGrams: Double = DEFAULT_FIBER_GRAMS,
    )

    fun macroTargetsFor(dailyCalories: Int): MacroTargets {
        if (dailyCalories <= 0) {
            return MacroTargets(0.0, 0.0, 0.0, 0.0)
        }
        return MacroTargets(
            proteinGrams = dailyCalories * PROTEIN_RATIO / KCAL_PER_PROTEIN_GRAM,
            carbsGrams = dailyCalories * CARB_RATIO / KCAL_PER_CARB_GRAM,
            fatGrams = dailyCalories * FAT_RATIO / KCAL_PER_FAT_GRAM,
            fiberGrams = DEFAULT_FIBER_GRAMS,
        )
    }

    private const val PROTEIN_RATIO = 0.30
    private const val CARB_RATIO = 0.40
    private const val FAT_RATIO = 0.30
    private const val KCAL_PER_PROTEIN_GRAM = 4.0
    private const val KCAL_PER_CARB_GRAM = 4.0
    private const val KCAL_PER_FAT_GRAM = 9.0
    private const val DEFAULT_FIBER_GRAMS = 30.0
}
