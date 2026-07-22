package com.example.calories.insights

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InsightWeekKeysTest {
    @Test
    fun isoWeekKey_formatsYearAndWeek() {
        // 2026-07-22 is Wednesday of ISO week 30
        assertEquals("2026-W30", InsightWeekKeys.isoWeekKey(LocalDate.of(2026, 7, 22)))
    }
}
