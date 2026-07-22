package com.example.calories.insights

import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

object ProgressInsightEngine {

    fun evaluate(input: InsightEngineInput): List<ProgressInsight> {
        if (input.dailyCalorieTarget <= 0) return emptyList()

        val windowStart = input.today.minusDays(ProgressInsightThresholds.WINDOW_DAYS - 1)
        val foodsInWindow = input.foodDays
            .filter { it.date in windowStart..input.today }
            .groupBy { it.date }
            .map { (date, days) ->
                InsightFoodDay(
                    date = date,
                    calories = days.sumOf { it.calories },
                    proteinGrams = days.sumOf { it.proteinGrams },
                )
            }
            .sortedBy { it.date }

        val foodDayCount = foodsInWindow.size
        if (foodDayCount < ProgressInsightThresholds.MIN_FOOD_LOG_DAYS) {
            return listOf(
                ProgressInsight(
                    id = ProgressInsightIds.INSUFFICIENT_DATA,
                    severity = InsightSeverity.INFO,
                ),
            )
        }

        // Later tasks append rule detections here, then rank/take.
        return emptyList()
    }

    fun selectHomeCallout(
        insights: List<ProgressInsight>,
        dismissedIds: Set<String>,
    ): ProgressInsight? {
        val visible = insights.filter { it.id !in dismissedIds }
        visible.firstOrNull { it.severity == InsightSeverity.ACTIONABLE }?.let { return it }
        return visible.firstOrNull { it.id == ProgressInsightIds.LOGGING_GAP }
    }
}
