package com.example.calories.widget

import com.example.calories.data.auth.ActiveUserIdProvider
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.repository.WaterRepository
import com.example.calories.model.WaterEntry
import com.example.calories.util.WaterDefaults
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetWaterActionHandlerTest {

    private val activeUserIdProvider: ActiveUserIdProvider = mockk(relaxed = true)
    private val userGoalDao: UserGoalDao = mockk(relaxed = true)

    @Test
    fun addWaterForUser_whenSignedOut_returnsFalse() = runTest {
        val handler = createHandler(RecordingWaterRepository())

        val result = handler.addWaterForUser(null)

        assertFalse(result)
    }

    @Test
    fun addWaterForUser_whenSignedIn_addsWaterAndRefreshes() = runTest {
        val repository = RecordingWaterRepository()
        var notifyCount = 0
        val handler = createHandler(repository) { notifyCount++ }

        val result = handler.addWaterForUser("user-1")

        assertTrue(result)
        assertEquals(WaterDefaults.STEP_ML, repository.lastAmountMl)
        assertEquals("user-1", repository.lastUserId)
        assertEquals(1, notifyCount)
    }

    @Test
    fun addWaterFromWidget_fallsBackToUserGoalDaoWhenSessionUnavailable() = runTest {
        val repository = RecordingWaterRepository()
        every { activeUserIdProvider.get() } returns null
        coEvery { userGoalDao.getAnyUserId() } returns "goal-user"

        val handler = WidgetWaterActionHandler(
            waterRepository = repository,
            widgetRefresher = WidgetRefresher {},
            activeUserIdProvider = activeUserIdProvider,
            userGoalDao = userGoalDao,
        )
        val result = handler.addWaterFromWidget()

        assertTrue(result)
        assertEquals("goal-user", repository.lastUserId)
    }

    private fun createHandler(
        repository: WaterRepository,
        onRefresh: () -> Unit = {},
    ): WidgetWaterActionHandler {
        every { activeUserIdProvider.get() } returns null
        coEvery { userGoalDao.getAnyUserId() } returns null
        return WidgetWaterActionHandler(
            waterRepository = repository,
            widgetRefresher = WidgetRefresher(onRefresh),
            activeUserIdProvider = activeUserIdProvider,
            userGoalDao = userGoalDao,
        )
    }

    private class RecordingWaterRepository : WaterRepository {
        var lastAmountMl: Int? = null
        var lastUserId: String? = null

        override fun observeWaterEntries(userId: String): Flow<List<WaterEntry>> = emptyFlow()

        override fun observeTotalMlForDate(userId: String, date: LocalDate): Flow<Int> = emptyFlow()

        override suspend fun addWaterEntry(
            amountMl: Int,
            createdAt: String,
            userIdOverride: String?,
        ): WaterEntry {
            lastAmountMl = amountMl
            lastUserId = userIdOverride
            return WaterEntry("w1", userIdOverride ?: "user-1", amountMl, createdAt)
        }

        override suspend fun removeLastForDate(date: LocalDate, amountMl: Int): Boolean = false

        override suspend fun deleteWaterEntry(id: String) = Unit

        override suspend fun fetchAndSync() = Unit

        override suspend fun refresh(userId: String) = Unit

        override suspend fun getTotalMlForDate(userId: String, date: LocalDate): Int = 0
    }
}
