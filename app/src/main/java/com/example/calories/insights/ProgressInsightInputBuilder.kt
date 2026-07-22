package com.example.calories.insights

import com.example.calories.model.FoodEntry
import com.example.calories.model.WeightEntry
import com.example.calories.util.CalorieCalculator
import com.example.calories.util.DateTimeUtils
import java.time.LocalDate

object ProgressInsightInputBuilder {

    fun build(
        foods: List<FoodEntry>,
        weights: List<WeightEntry>,
        dailyCalories: Int?,
        today: LocalDate = DateTimeUtils.today(),
    ): List<ProgressInsight> {
        if (dailyCalories == null || dailyCalories <= 0) return emptyList()
        val macros = CalorieCalculator.macroTargetsFor(dailyCalories)
        val foodDays = foods.mapNotNull { entry ->
            val date = DateTimeUtils.toLocalDate(entry.createdAt) ?: return@mapNotNull null
            InsightFoodDay(date, entry.calories, entry.protein)
        }
        val weightPoints = weights.mapNotNull { entry ->
            val date = DateTimeUtils.toLocalDate(entry.recordedAt) ?: return@mapNotNull null
            InsightWeightPoint(date, entry.weightKg)
        }
        return ProgressInsightEngine.evaluate(
            InsightEngineInput(
                today = today,
                dailyCalorieTarget = dailyCalories,
                proteinTargetGrams = macros.proteinGrams,
                foodDays = foodDays,
                weights = weightPoints,
            ),
        )
    }
}
