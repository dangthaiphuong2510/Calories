package com.example.calories.notifications

object ReminderIds {
    const val MEAL_BREAKFAST = 1001
    const val MEAL_LUNCH = 1002
    const val MEAL_DINNER = 1003
    const val MEAL_SNACKS = 1004

    const val WATER_BASE = 2000
    const val WORKOUT_BASE = 3000
    const val MAX_SCHEDULE_SLOTS = 24

    fun water(index: Int): Int = WATER_BASE + index
    fun workout(index: Int): Int = WORKOUT_BASE + index
}
