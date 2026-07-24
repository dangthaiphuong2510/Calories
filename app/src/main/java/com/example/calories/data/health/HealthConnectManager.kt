package com.example.calories.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.calories.util.DateTimeUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val zone: ZoneId = ZoneId.systemDefault()

    val isAvailable: Boolean
        get() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    val stepsReadPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
    )

    private val client: HealthConnectClient?
        get() = if (isAvailable) HealthConnectClient.getOrCreate(context) else null

    suspend fun hasStepsPermission(): Boolean {
        val healthConnectClient = client ?: return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(stepsReadPermissions)
    }

    suspend fun readTodaySteps(): Long {
        return readDailyStepsFromYesterday()[DateTimeUtils.today().toString()] ?: 0L
    }

    /**
     * Reads step totals from 00:00 yesterday through now, grouped by local calendar date.
     */
    suspend fun readDailyStepsFromYesterday(): Map<String, Long> {
        val healthConnectClient = client ?: return emptyMap()
        val today = DateTimeUtils.today()
        val yesterday = today.minusDays(1)
        val now = Instant.now()
        return buildDailyStepTotals(healthConnectClient, yesterday, today, now)
    }

    private suspend fun buildDailyStepTotals(
        healthConnectClient: HealthConnectClient,
        fromDate: LocalDate,
        toDate: LocalDate,
        endInstant: Instant,
    ): Map<String, Long> {
        val totals = linkedMapOf<String, Long>()
        var date = fromDate
        while (!date.isAfter(toDate)) {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = if (date == toDate) {
                endInstant
            } else {
                date.plusDays(1).atStartOfDay(zone).toInstant()
            }
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd),
                ),
            )
            totals[date.toString()] = response[StepsRecord.COUNT_TOTAL] ?: 0L
            date = date.plusDays(1)
        }
        return totals
    }
}
