package com.example.calories.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCalorieCalculatorTest {

    @Test
    fun caloriesFromSteps_usesStandardEstimate() {
        assertEquals(174, StepCalorieCalculator.caloriesFromSteps(4_350))
    }

    @Test
    fun caloriesFromSteps_roundsToNearestKcal() {
        assertEquals(2, StepCalorieCalculator.caloriesFromSteps(50))
    }

    @Test
    fun caloriesFromSteps_zeroSteps_returnsZero() {
        assertEquals(0, StepCalorieCalculator.caloriesFromSteps(0))
    }
}
