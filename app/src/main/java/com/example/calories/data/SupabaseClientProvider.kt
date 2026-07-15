package com.example.calories.data

import android.util.Log
import com.example.calories.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClientProvider {

    private const val TAG = "SupabaseClient"

    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        check(url.isNotBlank() && !url.contains("YOUR_PROJECT")) {
            "SUPABASE_URL is not configured. Add it to local.properties."
        }
        check(key.isNotBlank() && key != "YOUR_ANON_KEY") {
            "SUPABASE_ANON_KEY is not configured. Add it to local.properties."
        }
        Log.i(TAG, "Connecting to Supabase host=${url.removePrefix("https://").substringBefore("/")}")
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            install(Postgrest)
            install(Auth)
        }
    }
}
