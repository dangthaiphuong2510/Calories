package com.example.calories.widget

import com.example.calories.data.auth.ActiveUserIdProvider
import com.example.calories.data.local.dao.UserGoalDao
import com.example.calories.data.repository.WaterRepository
import com.example.calories.util.DateTimeUtils
import com.example.calories.util.WaterDefaults
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetWaterActionHandler @Inject constructor(
    private val waterRepository: WaterRepository,
    private val widgetRefresher: WidgetRefresher,
    private val activeUserIdProvider: ActiveUserIdProvider,
    private val userGoalDao: UserGoalDao,
) {
    suspend fun addWaterFromWidget(): Boolean {
        val userId = resolveUserId() ?: return false
        return addWaterForUser(userId)
    }

    internal suspend fun resolveUserId(): String? {
        return activeUserIdProvider.get() ?: userGoalDao.getAnyUserId()
    }

    internal suspend fun addWaterForUser(userId: String?): Boolean {
        if (userId.isNullOrBlank()) return false

        waterRepository.addWaterEntry(
            amountMl = WaterDefaults.STEP_ML,
            createdAt = DateTimeUtils.nowIso(),
            userIdOverride = userId,
        )

        widgetRefresher.notifyDataChanged()
        return true
    }
}