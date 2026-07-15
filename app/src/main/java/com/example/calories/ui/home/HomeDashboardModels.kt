package com.example.calories.ui.home

import com.example.calories.R
import com.example.calories.model.enums.MealType
import java.time.LocalDate

data class MacroProgress(
    val currentGrams: Double = 0.0,
    val targetGrams: Double = 0.0,
) {
    val progressPercent: Int
        get() = if (targetGrams <= 0.0) {
            0
        } else {
            ((currentGrams / targetGrams) * 100.0).toInt().coerceIn(0, 100)
        }
}

data class MealFoodItem(
    val id: String,
    val name: String,
    val calories: Int,
    val servingGrams: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
)

data class MealSection(
    val mealType: MealType,
    val titleRes: Int,
    val foods: List<MealFoodItem> = emptyList(),
    val showLoggedFeedback: Boolean = false,
) {
    val totalCalories: Int get() = foods.sumOf { it.calories }
    val totalProtein: Double get() = foods.sumOf { it.protein }
    val totalCarb: Double get() = foods.sumOf { it.carb }
    val totalFat: Double get() = foods.sumOf { it.fat }
    val hasFoods: Boolean get() = foods.isNotEmpty()
}

data class ExerciseLogItem(
    val id: String,
    val name: String,
    val caloriesBurned: Int,
)

data class HomeUiState(
    val currentDate: LocalDate = LocalDate.now(),
    val currentDateLabel: String = "",
    val dailyGoal: Int = 0,
    val totalEaten: Int = 0,
    val totalBurned: Int = 0,
    val waterIntakeMl: Int = 0,
    val waterGoalMl: Int = 0,
    val waterStepMl: Int = 250,
    val protein: MacroProgress = MacroProgress(),
    val carbs: MacroProgress = MacroProgress(),
    val fat: MacroProgress = MacroProgress(),
    val fiber: MacroProgress = MacroProgress(),
    val breakfast: MealSection = MealSection(MealType.BREAKFAST, R.string.meal_breakfast),
    val lunch: MealSection = MealSection(MealType.LUNCH, R.string.meal_lunch),
    val dinner: MealSection = MealSection(MealType.DINNER, R.string.meal_dinner),
    val snacks: MealSection = MealSection(MealType.SNACK, R.string.meal_snacks),
    val exercises: List<ExerciseLogItem> = emptyList(),
    val todayWeightKg: Double? = null,
    val mealDetailsExpanded: Boolean = false,
) {
    val caloriesRemaining: Int
        get() = (dailyGoal - totalEaten + totalBurned).coerceAtLeast(0)

    val calorieProgressPercent: Int
        get() = if (dailyGoal <= 0) {
            0
        } else {
            ((totalEaten.toFloat() / dailyGoal) * 100f).toInt().coerceIn(0, 100)
        }

    val waterProgressPercent: Int
        get() = if (waterGoalMl <= 0) {
            0
        } else {
            ((waterIntakeMl.toFloat() / waterGoalMl) * 100f).toInt().coerceIn(0, 100)
        }

    val mealSections: List<MealSection>
        get() = listOf(breakfast, lunch, dinner, snacks)
}

sealed interface HomeNavEvent {
    data class OpenSearchFood(val mealType: MealType) : HomeNavEvent
    data class OpenFoodDetail(
        val foodId: String,
        val name: String,
        val calories: Int,
        val protein: Double,
        val carb: Double,
        val fat: Double,
        val servingGrams: Double,
        val mealType: MealType,
        val viewOnly: Boolean,
    ) : HomeNavEvent
    data object OpenExerciseLogger : HomeNavEvent
}
