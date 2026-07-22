package com.example.calories.insights

import java.time.LocalDate
import java.time.temporal.WeekFields

object InsightWeekKeys {
    fun isoWeekKey(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "%d-W%02d".format(year, week)
    }
}
