package com.example.calories.insights

import java.time.LocalDate

object ProgressInsightIds {
    const val PLATEAU_UNDER_TARGET = "plateau_under_target"
    const val WEEKEND_CALORIE_SPIKE = "weekend_calorie_spike"
    const val PROTEIN_SHORTFALL = "protein_shortfall"
    const val LOGGING_GAP = "logging_gap"
    const val ON_TRACK_STREAK = "on_track_streak"
    const val INSUFFICIENT_DATA = "insufficient_data"
}

enum class InsightSeverity {
    ACTIONABLE,
    INFO,
    POSITIVE,
}

sealed class InsightAction {
    data object OpenProgress : InsightAction()
    data object OpenWeightLog : InsightAction()
}

data class ProgressInsight(
    val id: String,
    val severity: InsightSeverity,
    val formatArgs: List<String> = emptyList(),
    val action: InsightAction? = null,
)

/** One calendar day of aggregated food intake (already summed by the caller). */
data class InsightFoodDay(
    val date: LocalDate,
    val calories: Int,
    val proteinGrams: Double,
)

data class InsightWeightPoint(
    val date: LocalDate,
    val weightKg: Double,
)

data class InsightEngineInput(
    val today: LocalDate,
    val dailyCalorieTarget: Int,
    val proteinTargetGrams: Double,
    val foodDays: List<InsightFoodDay>,
    val weights: List<InsightWeightPoint>,
)
