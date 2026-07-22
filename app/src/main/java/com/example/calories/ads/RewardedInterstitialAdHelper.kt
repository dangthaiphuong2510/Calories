package com.example.calories.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

class RewardedInterstitialAdHelper(
    private val activity: Activity,
    private val adUnitId: String = REWARDED_INTERSTITIAL_AD_UNIT_ID,
) {

    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isLoading = false

    fun preload() {
        if (rewardedInterstitialAd != null || isLoading) return
        isLoading = true
        RewardedInterstitialAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedInterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedInterstitialAd) {
                    isLoading = false
                    rewardedInterstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedInterstitialAd = null
                }
            },
        )
    }

    fun showAd(onProceed: () -> Unit) {
        val ad = rewardedInterstitialAd
        if (ad == null) {
            onProceed()
            preload()
            return
        }

        var proceeded = false
        fun proceedOnce() {
            if (!proceeded) {
                proceeded = true
                onProceed()
            }
        }

        ad.fullScreenContentCallback = FullscreenAdWindowHelper.wrapCallback(activity,
            object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedInterstitialAd = null
                    preload()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedInterstitialAd = null
                    preload()
                    proceedOnce()
                }
            },
        )

        FullscreenAdWindowHelper.enterFullscreenAdMode(activity)
        ad.show(activity) { proceedOnce() }
    }

    fun destroy() {
        rewardedInterstitialAd?.fullScreenContentCallback = null
        rewardedInterstitialAd = null
        isLoading = false
    }

    companion object {
        const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379"
    }
}
