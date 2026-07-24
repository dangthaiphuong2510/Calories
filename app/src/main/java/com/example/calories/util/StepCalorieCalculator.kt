package com.example.calories.util

import kotlin.math.roundToInt

object StepCalorieCalculator {

    /** Standard active-calorie estimate per step (kcal). */
    const val KCAL_PER_STEP = 0.04

    fun caloriesFromSteps(steps: Long): Int =
        caloriesBurnedFromSteps(steps).roundToInt().coerceAtLeast(0)

    fun caloriesBurnedFromSteps(steps: Long): Double =
        (steps * KCAL_PER_STEP).coerceAtLeast(0.0)
}
