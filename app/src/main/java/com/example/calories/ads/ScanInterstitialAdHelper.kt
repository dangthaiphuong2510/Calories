package com.example.calories.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Loads a scan-flow interstitial and shows it only after [InterstitialAdLoadCallback.onAdLoaded].
 * Navigation should happen from [FullScreenContentCallback.onAdDismissedFullScreenContent].
 */
class ScanInterstitialAdHelper(
    private val activity: Activity,
    private val adUnitId: String = SCAN_INTERSTITIAL_AD_UNIT_ID,
) {

    private var interstitialAd: InterstitialAd? = null

    fun loadAndShow(
        onAdDismissed: () -> Unit,
        onAdUnavailable: () -> Unit,
    ) {
        destroy()

        InterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            destroy()
                            onAdDismissed()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: AdError) {
                            destroy()
                            onAdUnavailable()
                        }
                    }
                    ad.show(activity)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    destroy()
                    onAdUnavailable()
                }
            },
        )
    }

    fun destroy() {
        interstitialAd?.fullScreenContentCallback = null
        interstitialAd = null
    }

    companion object {
        const val SCAN_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    }
}
