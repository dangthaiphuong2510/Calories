package com.example.calories.data.remote.supabase

import com.example.calories.model.WeightEntry
import com.example.calories.model.WeightEntryInsert
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseWeightService @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun addWeightEntry(weightKg: Double, recordedAt: String): WeightEntry {
        val userId = requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .insert(
                WeightEntryInsert(
                    userId = userId,
                    weightKg = weightKg,
                    recordedAt = recordedAt,
                ),
            ) {
                select()
            }
            .decodeSingle<WeightEntry>()
    }

    suspend fun getAllWeightEntries(): List<WeightEntry> {
        requireCurrentUserId()
        return supabase.from(TABLE_NAME)
            .select {
                order(column = RECORDED_AT_COLUMN, order = Order.ASCENDING)
            }
            .decodeList<WeightEntry>()
    }

    suspend fun deleteWeightEntry(id: String) {
        requireCurrentUserId()
        supabase.from(TABLE_NAME)
            .delete {
                filter {
                    eq("id", id)
                }
            }
    }

    private fun requireCurrentUserId(): String {
        return supabase.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User chưa đăng nhập")
    }

    private companion object {
        const val TABLE_NAME = "weight_entries"
        const val RECORDED_AT_COLUMN = "recorded_at"
    }
}
