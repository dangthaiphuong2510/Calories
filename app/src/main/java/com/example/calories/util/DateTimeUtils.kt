package com.example.calories.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeUtils {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val displayDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
    private val shortDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val expiryDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val chartDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun todayRange(): Pair<String, String> {
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant().toString()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toString()
        return startOfDay to startOfTomorrow
    }

    fun nowIso(): String = Instant.now().toString()

    fun todayDisplay(): String = LocalDate.now(zone).format(displayDate)

    fun formatDisplayDate(iso: String): String {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate().format(shortDate)
        }.getOrElse { iso }
    }

    fun formatChartLabel(iso: String): String {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate().format(chartDate)
        }.getOrElse { iso }
    }

    fun formatExpiry(isoDate: String): String {
        return runCatching {
            LocalDate.parse(isoDate).format(expiryDate)
        }.getOrElse { isoDate }
    }

    fun isToday(iso: String): Boolean {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate() == LocalDate.now(zone)
        }.getOrDefault(false)
    }

    fun toLocalDate(iso: String): LocalDate? {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate()
        }.getOrNull()
    }
}
