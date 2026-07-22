package com.example.calories.ads

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowCompat
import com.example.calories.ui.common.EdgeToEdgeHost
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import java.util.WeakHashMap

/**
 * Prevents edge-to-edge app content from leaking through the status/navigation bars
 * while a full-screen AdMob ad is visible.
 */
object FullscreenAdWindowHelper {

    private data class WindowState(
        val statusBarColor: Int,
        val navigationBarColor: Int,
        val isEdgeToEdge: Boolean,
        val lightStatusBars: Boolean,
        val lightNavigationBars: Boolean,
        val decorBackgroundColor: Int,
    )

    private val savedStates = WeakHashMap<Activity, WindowState>()

    fun enterFullscreenAdMode(activity: Activity) {
        if (savedStates.containsKey(activity)) return

        val window = activity.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)
        val decorBackgroundColor = when (val background = decorView.background) {
            is ColorDrawable -> background.color
            else -> Color.TRANSPARENT
        }

        savedStates[activity] = WindowState(
            statusBarColor = window.statusBarColor,
            navigationBarColor = window.navigationBarColor,
            isEdgeToEdge = usesEdgeToEdge(window.statusBarColor, window.navigationBarColor),
            lightStatusBars = controller.isAppearanceLightStatusBars,
            lightNavigationBars = controller.isAppearanceLightNavigationBars,
            decorBackgroundColor = decorBackgroundColor,
        )

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        decorView.setBackgroundColor(Color.BLACK)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    fun exitFullscreenAdMode(activity: Activity) {
        val state = savedStates.remove(activity) ?: return

        val window = activity.window
        val decorView = window.decorView
        val controller = WindowCompat.getInsetsController(window, decorView)

        WindowCompat.setDecorFitsSystemWindows(window, !state.isEdgeToEdge)
        window.statusBarColor = state.statusBarColor
        window.navigationBarColor = state.navigationBarColor
        decorView.setBackgroundColor(state.decorBackgroundColor)
        controller.isAppearanceLightStatusBars = state.lightStatusBars
        controller.isAppearanceLightNavigationBars = state.lightNavigationBars

        (activity as? EdgeToEdgeHost)?.restoreEdgeToEdgeAfterFullscreenAd()
    }

    private fun restoreAfterFullscreenAd(activity: Activity, delegate: FullScreenContentCallback?) {
        exitFullscreenAdMode(activity)
        delegate?.onAdDismissedFullScreenContent()
    }

    private fun restoreAfterFullscreenAdFailed(activity: Activity, error: AdError, delegate: FullScreenContentCallback?) {
        exitFullscreenAdMode(activity)
        delegate?.onAdFailedToShowFullScreenContent(error)
    }

    fun wrapCallback(
        activity: Activity,
        delegate: FullScreenContentCallback?,
    ): FullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdShowedFullScreenContent() {
            delegate?.onAdShowedFullScreenContent()
        }

        override fun onAdDismissedFullScreenContent() {
            restoreAfterFullscreenAd(activity, delegate)
        }

        override fun onAdFailedToShowFullScreenContent(error: AdError) {
            restoreAfterFullscreenAdFailed(activity, error, delegate)
        }
    }

    /** Edge-to-edge in this app uses transparent system-bar colors. */
    private fun usesEdgeToEdge(statusBarColor: Int, navigationBarColor: Int): Boolean =
        Color.alpha(statusBarColor) == 0 && Color.alpha(navigationBarColor) == 0
}
