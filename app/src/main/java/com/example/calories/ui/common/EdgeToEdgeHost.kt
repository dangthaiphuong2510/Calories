package com.example.calories.ui.common

/**
 * Activities that use [androidx.activity.enableEdgeToEdge] can implement this so
 * full-screen ads restore system bar styling and cached insets after dismiss.
 */
fun interface EdgeToEdgeHost {
    fun restoreEdgeToEdgeAfterFullscreenAd()
}
