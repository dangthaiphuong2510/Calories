package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
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

    private fun baseInput(
        foods: List<InsightFoodDay>,
        weights: List<InsightWeightPoint> = emptyList(),
        calories: Int = 2000,
        protein: Double = 150.0,
    ) = InsightEngineInput(
        today = today,
        dailyCalorieTarget = calories,
        proteinTargetGrams = protein,
        foodDays = foods,
        weights = weights,
    )

    /** 7 consecutive days with food logs. */
    private fun sevenDays(
        calories: Int,
        protein: Double = 150.0,
    ): List<InsightFoodDay> =
        (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), calories, protein)
        }

    @Test
    fun loggingGap_whenTwoOrMoreDaysMissingInWindow() {
        // Only 5 of 7 days logged → 2 missing
        val foods = listOf(0, 1, 2, 4, 5).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.LOGGING_GAP))
        assertTrue(ProgressInsightIds.INSUFFICIENT_DATA !in ids)
    }

    @Test
    fun proteinShortfall_whenFourOfLastFiveLoggedDaysBelow80Percent() {
        val foods = (6 downTo 0).map { offset ->
            // ~100g vs 150g target = 66% < 80%
            InsightFoodDay(today.minusDays(offset.toLong()), 2000, 100.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.PROTEIN_SHORTFALL))
    }

    @Test
    fun onTrackStreak_whenFiveDaysWithinTenPercentOfTarget() {
        val foods = sevenDays(calories = 2000, protein = 150.0)
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.ON_TRACK_STREAK))
    }

    @Test
    fun weekendSpike_whenWeekendAvgAtLeast15PercentAboveWeekday() {
        // Build Mon–Sun ending on today=Wed 2026-07-22: use explicit dates in the window
        val foods = mutableListOf<InsightFoodDay>()
        // last 7 days: Thu 16 … Wed 22
        for (offset in 6 downTo 0) {
            val date = today.minusDays(offset.toLong())
            val cal = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
                else -> 1800
            }
            foods += InsightFoodDay(date, cal, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.WEEKEND_CALORIE_SPIKE))
    }

    @Test
    fun plateau_whenUnderTargetOftenAndWeightFlat() {
        val foods = (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0) // under 2000
        }
        val weights = listOf(
            InsightWeightPoint(today.minusDays(10), 80.0),
            InsightWeightPoint(today, 80.2), // delta 0.2 < 0.4
        )
        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights)).map { it.id }
        assertTrue(ids.contains(ProgressInsightIds.PLATEAU_UNDER_TARGET))
    }

    @Test
    fun plateau_skippedWhenFewerThanTwoWeights() {
        val foods = (6 downTo 0).map { offset ->
            InsightFoodDay(today.minusDays(offset.toLong()), 1500, 150.0)
        }
        val ids = ProgressInsightEngine.evaluate(baseInput(foods, weights = emptyList())).map { it.id }
        assertTrue(ProgressInsightIds.PLATEAU_UNDER_TARGET !in ids)
    }
}
