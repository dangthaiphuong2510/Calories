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
    private val homeMonthDay: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val shortDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    private val ddMmYyyy: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
    private val expiryDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val chartDate: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

    fun today(): LocalDate = LocalDate.now(zone)

    fun todayRange(): Pair<String, String> = dayRange(today())

    fun dayRange(date: LocalDate): Pair<String, String> {
        val startOfDay = date.atStartOfDay(zone).toInstant().toString()
        val startOfTomorrow = date.plusDays(1).atStartOfDay(zone).toInstant().toString()
        return startOfDay to startOfTomorrow
    }

    fun nowIso(): String = Instant.now().toString()

    fun todayDisplay(): String = today().format(displayDate)

    fun formatDdMmYyyy(date: LocalDate): String = date.format(ddMmYyyy)

    fun formatMonthDay(date: LocalDate): String = date.format(homeMonthDay)

    fun formatWeekdayMonthDay(date: LocalDate): String = date.format(displayDate)

    fun atNoonIso(date: LocalDate): String =
        date.atTime(12, 0).atZone(zone).toInstant().toString()

    /** Timestamp on [date] using the current clock time (unique per call). */
    fun atNowOnDateIso(date: LocalDate): String {
        val time = java.time.LocalTime.now(zone)
        return date.atTime(time).atZone(zone).toInstant().toString()
    }

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
            Instant.parse(iso).atZone(zone).toLocalDate() == today()
        }.getOrDefault(false)
    }

    fun isSameDay(iso: String, date: LocalDate): Boolean {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate() == date
        }.getOrDefault(false)
    }

    fun toLocalDate(iso: String): LocalDate? {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate()
        }.getOrNull()
    }
}
