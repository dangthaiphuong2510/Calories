package com.example.calories.widget

import com.example.calories.insights.ProgressInsightIds
import com.example.calories.model.ExerciseEntry
import com.example.calories.model.FoodEntry
import com.example.calories.model.UserGoal
import com.example.calories.model.WeightEntry
import com.example.calories.model.enums.ActivityLevel
import com.example.calories.model.enums.Gender
import com.example.calories.model.enums.GoalType
import com.example.calories.model.enums.MealType
import com.example.calories.util.DateTimeUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class CaloriesWidgetSnapshotBuilderTest {

    private val today = LocalDate.of(2026, 7, 22)
    private val userId = "user-1"

    private fun goal(dailyCalories: Int = 2000) = UserGoal(
        id = "g1",
        userId = userId,
        targetWeight = 70.0,
        currentWeight = 80.0,
        age = 30,
        gender = Gender.MALE,
        heightCm = 175.0,
        activityLevel = ActivityLevel.MODERATE,
        goalType = GoalType.LOSE_WEIGHT,
        tdee = 2200,
        dailyCalories = dailyCalories,
    )

    private fun food(calories: Int, date: LocalDate = today) = FoodEntry(
        id = "f-$calories-${date}",
        userId = userId,
        name = "Food",
        calories = calories,
        protein = 20.0,
        carb = 10.0,
        fat = 5.0,
        mealType = MealType.LUNCH,
        servingGrams = 100.0,
        createdAt = DateTimeUtils.atNoonIso(date),
    )

    @Test
    fun signedOut_returnsSignedOutSnapshot() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = false,
            goal = null,
            todayFoods = emptyList(),
            allFoods = emptyList(),
            weights = emptyList(),
            todayExercises = emptyList(),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(CaloriesWidgetDisplayMode.SIGNED_OUT, snapshot.displayMode)
    }

    @Test
    fun calorieMath_matchesHomeUiState() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = goal(),
            todayFoods = listOf(food(1200)),
            allFoods = listOf(food(1200)),
            weights = emptyList(),
            todayExercises = listOf(
                ExerciseEntry(
                    id = "e1",
                    userId = userId,
                    name = "Run",
                    caloriesBurned = 230.0,
                    durationMinutes = 30,
                    createdAt = DateTimeUtils.atNoonIso(today),
                ),
            ),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(2000, snapshot.dailyGoal)
        assertEquals(1200, snapshot.totalEaten)
        assertEquals(230, snapshot.totalBurned)
        assertEquals(1030, snapshot.caloriesRemaining)
        assertEquals(60, snapshot.progressPercent)
    }

    @Test
    fun dismissedActionableInsight_isSkipped() {
        val foods = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val calories = when (date.dayOfWeek) {
                DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> 2500
                else -> 1800
            }
            food(calories, date)
        }
        val weights = listOf(
            WeightEntry("w1", userId, 80.0, DateTimeUtils.atNoonIso(today.minusDays(10))),
            WeightEntry("w2", userId, 80.1, DateTimeUtils.atNoonIso(today)),
        )
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = goal(),
            todayFoods = foods.filter { DateTimeUtils.isSameDay(it.createdAt, today) },
            allFoods = foods,
            weights = weights,
            todayExercises = emptyList(),
            dismissedIds = setOf(ProgressInsightIds.PLATEAU_UNDER_TARGET),
            today = today,
        )
        assertEquals(ProgressInsightIds.WEEKEND_CALORIE_SPIKE, snapshot.activeInsight?.id)
    }

    @Test
    fun noGoal_hidesInsightSection() {
        val snapshot = CaloriesWidgetSnapshotBuilder.build(
            isSignedIn = true,
            goal = null,
            todayFoods = listOf(food(500)),
            allFoods = listOf(food(500)),
            weights = emptyList(),
            todayExercises = emptyList(),
            dismissedIds = emptySet(),
            today = today,
        )
        assertEquals(CaloriesWidgetDisplayMode.NO_GOAL, snapshot.displayMode)
        assertNull(snapshot.activeInsight)
    }
}
