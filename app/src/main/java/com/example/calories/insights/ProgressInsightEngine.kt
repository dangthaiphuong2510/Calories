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

        val insights = mutableListOf<ProgressInsight>()

        val missingDays = ProgressInsightThresholds.WINDOW_DAYS.toInt() - foodDayCount
        if (missingDays >= ProgressInsightThresholds.LOGGING_GAP_MISSING_DAYS) {
            insights += ProgressInsight(
                id = ProgressInsightIds.LOGGING_GAP,
                severity = InsightSeverity.INFO,
                formatArgs = listOf(missingDays.toString()),
                action = InsightAction.OpenProgress,
            )
        }

        val loggedLookbackStart =
            input.today.minusDays(ProgressInsightThresholds.LOGGED_LOOKBACK_DAYS - 1)
        val recentLogged = input.foodDays
            .filter { it.date in loggedLookbackStart..input.today }
            .groupBy { it.date }
            .map { (date, days) ->
                InsightFoodDay(date, days.sumOf { it.calories }, days.sumOf { it.proteinGrams })
            }
            .sortedByDescending { it.date }

        val proteinSample = recentLogged.take(ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE)
        if (proteinSample.size >= ProgressInsightThresholds.PROTEIN_LOGGED_SAMPLE &&
            input.proteinTargetGrams > 0
        ) {
            val shortDays = proteinSample.count { day ->
                day.proteinGrams <
                    input.proteinTargetGrams * ProgressInsightThresholds.PROTEIN_RATIO_THRESHOLD
            }
            if (shortDays >= ProgressInsightThresholds.PROTEIN_SHORTFALL_DAYS_NEEDED) {
                insights += ProgressInsight(
                    id = ProgressInsightIds.PROTEIN_SHORTFALL,
                    severity = InsightSeverity.ACTIONABLE,
                    action = InsightAction.OpenProgress,
                )
            }
        }

        val onTrackCount = foodsInWindow.count { day ->
            val target = input.dailyCalorieTarget.toDouble()
            val lo = target * (1.0 - ProgressInsightThresholds.ON_TRACK_TOLERANCE)
            val hi = target * (1.0 + ProgressInsightThresholds.ON_TRACK_TOLERANCE)
            day.calories.toDouble() in lo..hi
        }
        if (onTrackCount >= ProgressInsightThresholds.ON_TRACK_DAYS_NEEDED) {
            insights += ProgressInsight(
                id = ProgressInsightIds.ON_TRACK_STREAK,
                severity = InsightSeverity.POSITIVE,
                formatArgs = listOf(onTrackCount.toString()),
            )
        }

        return insights.take(ProgressInsightThresholds.MAX_PROGRESS_INSIGHTS)
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
