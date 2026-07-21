package com.example.calories.model

/**
 * Aggregated nutrition metrics for PDF export over a selected date range.
 */
data class NutritionReportData(
    val userName: String,
    val dateRangeText: String,
    val targetCaloriesPerDay: Int,
    val avgCaloriesIntake: Int,
    val avgProteinGrams: Double,
    val avgCarbsGrams: Double,
    val avgFatGrams: Double,
    val totalWaterMl: Int,
)
