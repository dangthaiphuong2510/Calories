package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressInsightEngineTest {

    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun emptyFoods_returnsOnlyInsufficientData() {
        val result = ProgressInsightEngine.evaluate(
            InsightEngineInput(
                today = today,
                dailyCalorieTarget = 2000,
                proteinTargetGrams = 150.0,
                foodDays = emptyList(),
                weights = listOf(
                    InsightWeightPoint(today.minusDays(3), 80.0),
                    InsightWeightPoint(today, 80.1),
                ),
            ),
        )
        assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
    }

    @Test
    fun fewerThanThreeFoodDays_returnsOnlyInsufficientData() {
        val foods = listOf(
            InsightFoodDay(today.minusDays(1), calories = 1800, proteinGrams = 100.0),
            InsightFoodDay(today, calories = 1800, proteinGrams = 100.0),
        )
        val result = ProgressInsightEngine.evaluate(
            InsightEngineInput(
                today = today,
                dailyCalorieTarget = 2000,
                proteinTargetGrams = 150.0,
                foodDays = foods,
                weights = emptyList(),
            ),
        )
        assertEquals(listOf(ProgressInsightIds.INSUFFICIENT_DATA), result.map { it.id })
    }
}
