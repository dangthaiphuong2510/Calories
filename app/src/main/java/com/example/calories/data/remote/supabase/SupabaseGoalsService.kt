package com.example.calories.data.remote.supabase

import com.example.calories.model.UserGoal
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseGoalsService @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun upsertGoal(goal: UserGoal): UserGoal {
        requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .upsert(goal) {
                select()
            }
            .decodeSingle<UserGoal>()
    }

    suspend fun getGoalForCurrentUser(): UserGoal? {
        requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .select {
                limit(1)
            }
            .decodeList<UserGoal>()
            .firstOrNull()
    }

    private fun requireCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TABLE_NAME = "user_goals"
    }
}
