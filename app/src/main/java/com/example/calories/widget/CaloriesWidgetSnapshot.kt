package com.example.calories.widget

import com.example.calories.insights.ProgressInsight
import com.example.calories.insights.ProgressInsightInputBuilder
import com.example.calories.insights.ProgressInsightEngine
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.UserGoal
import com.example.calories.model.WeightEntry
import com.example.calories.util.WaterDefaults
import java.time.LocalDate
import kotlin.math.roundToInt

enum class CaloriesWidgetDisplayMode {
    SIGNED_OUT,
    NO_GOAL,
    READY,
}

data class CaloriesWidgetSnapshot(
    val displayMode: CaloriesWidgetDisplayMode,
    val dailyGoal: Int = 0,
    val totalEaten: Int = 0,
    val totalBurned: Int = 0,
    val caloriesRemaining: Int = 0,
    val progressPercent: Int = 0,
    val waterIntakeMl: Int = 0,
    val waterGoalMl: Int = WaterDefaults.GOAL_ML,
    val waterProgressPercent: Int = 0,
    val activeInsight: ProgressInsight? = null,
)

object CaloriesWidgetSnapshotBuilder {

    fun build(
        isSignedIn: Boolean,
        goal: UserGoal?,
        todayFoods: List<FoodEntry>,
        allFoods: List<FoodEntry>,
        weights: List<WeightEntry>,
        todayExercises: List<ExerciseEntry>,
        dismissedIds: Set<String>,
        waterIntakeMl: Int,
        today: LocalDate,
    ): CaloriesWidgetSnapshot {
        if (!isSignedIn) {
            return CaloriesWidgetSnapshot(displayMode = CaloriesWidgetDisplayMode.SIGNED_OUT)
        }

        val dailyGoal = goal?.dailyCalories ?: 0
        val totalEaten = todayFoods.sumOf { it.calories }
        val totalBurned = todayExercises.sumOf { it.caloriesBurned.roundToInt() }
        val caloriesRemaining = (dailyGoal - totalEaten + totalBurned).coerceAtLeast(0)
        val progressPercent = if (dailyGoal <= 0) {
            0
        } else {
            ((totalEaten.toFloat() / dailyGoal) * 100f).toInt().coerceIn(0, 100)
        }
        val waterGoalMl = WaterDefaults.GOAL_ML
        val waterProgressPercent = if (waterGoalMl <= 0) {
            0
        } else {
            ((waterIntakeMl.toFloat() / waterGoalMl) * 100f).toInt().coerceIn(0, 100)
        }

        val activeInsight = if (goal == null || dailyGoal <= 0) {
            null
        } else {
            val insights = ProgressInsightInputBuilder.build(
                foods = allFoods,
                weights = weights,
                dailyCalories = dailyGoal,
                today = today,
            )
            ProgressInsightEngine.selectHomeCallout(insights, dismissedIds)
        }

        val displayMode = when {
            dailyGoal <= 0 -> CaloriesWidgetDisplayMode.NO_GOAL
            else -> CaloriesWidgetDisplayMode.READY
        }

        return CaloriesWidgetSnapshot(
            displayMode = displayMode,
            dailyGoal = dailyGoal,
            totalEaten = totalEaten,
            totalBurned = totalBurned,
            caloriesRemaining = caloriesRemaining,
            progressPercent = progressPercent,
            waterIntakeMl = waterIntakeMl,
            waterGoalMl = waterGoalMl,
            waterProgressPercent = waterProgressPercent,
            activeInsight = activeInsight,
        )
    }
}
