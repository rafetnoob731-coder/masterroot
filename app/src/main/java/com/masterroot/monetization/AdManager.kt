package com.masterroot.monetization

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.masterroot.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdManager
 *
 * Centralizes all AdMob interactions.
 * Enforces the rule that ads NEVER appear during critical operations.
 *
 * Priority order (enforced in code):
 * 1. Device Safety
 * 2. Root Operation
 * 3. Root Verification
 * 4. Recovery
 * 5. User Experience
 * 6. Monetization ← ads live here only
 */
@Singleton
class AdManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val premiumManager: PremiumManager
) {

    // Test IDs from Google (safe to ship in source — these only work in debug)
    private object TestAdIds {
        const val BANNER     = "ca-app-pub-3940256099942544/6300978111"
        const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED   = "ca-app-pub-3940256099942544/5224354917"
    }

    // Production IDs — replace before release
    private object ProductionAdIds {
        const val BANNER       = "ca-app-pub-3940256099942544/6300978111"
        const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
        const val REWARDED     = "ca-app-pub-3940256099942544/5224354917"
    }

    private fun bannerId()       = if (BuildConfig.USE_TEST_ADS) TestAdIds.BANNER       else ProductionAdIds.BANNER
    private fun interstitialId() = if (BuildConfig.USE_TEST_ADS) TestAdIds.INTERSTITIAL else ProductionAdIds.INTERSTITIAL

    private var isInitialized = false
    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialShownMs = 0L
    private val interstitialCooldownMs = 60_000L // 60 seconds minimum between interstitials

    private val _criticalOperationActive = MutableStateFlow(false)
    val criticalOperationActive: StateFlow<Boolean> = _criticalOperationActive.asStateFlow()

    // ─── Initialization ───────────────────────────────────────────────────────

    fun initialize() {
        if (isInitialized) return
        MobileAds.initialize(context) { initStatus ->
            Timber.i("AdMob initialized: ${initStatus.adapterStatusMap}")
            isInitialized = true
            preloadInterstitial()
        }
    }

    // ─── Critical operation gating ────────────────────────────────────────────

    /**
     * Call this when entering a critical root operation.
     * All ads are suppressed until exitCriticalOperation() is called.
     */
    fun enterCriticalOperation() {
        _criticalOperationActive.value = true
        Timber.i("AdManager: critical operation active — ads suppressed")
    }

    /**
     * Call this when leaving a critical root operation.
     */
    fun exitCriticalOperation() {
        _criticalOperationActive.value = false
        Timber.i("AdManager: critical operation ended — ads may resume")
    }

    private fun canShowAd(): Boolean {
        if (!isInitialized) return false
        if (premiumManager.isPremium()) return false
        if (_criticalOperationActive.value) return false
        return true
    }

    // ─── Banner Ad ────────────────────────────────────────────────────────────

    fun createBannerAdView(): AdView? {
        if (!canShowAd()) return null
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = bannerId()
            loadAd(AdRequest.Builder().build())
        }
    }

    // ─── Interstitial ─────────────────────────────────────────────────────────

    private fun preloadInterstitial() {
        if (!canShowAd()) return
        InterstitialAd.load(
            context,
            interstitialId(),
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Timber.d("Interstitial preloaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Timber.d("Interstitial failed to load: ${error.message} — continuing normally")
                }
            }
        )
    }

    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit = {}) {
        if (!canShowAd()) {
            onDismissed()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShownMs < interstitialCooldownMs) {
            Timber.d("Interstitial skipped — within cooldown period")
            onDismissed()
            return
        }
        val ad = interstitialAd
        if (ad == null) {
            Timber.d("No interstitial ready — continuing without ad")
            onDismissed()
            preloadInterstitial() // preload for next time
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                lastInterstitialShownMs = System.currentTimeMillis()
                preloadInterstitial()
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Timber.d("Interstitial failed to show: ${error.message}")
                interstitialAd = null
                preloadInterstitial()
                onDismissed()
            }
        }
        ad.show(activity)
    }
}

// ─── Premium Manager ──────────────────────────────────────────────────────────

@Singleton
class PremiumManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("masterroot_premium", Context.MODE_PRIVATE)

    fun isPremium(): Boolean = prefs.getBoolean("is_premium", false)

    fun setPremium(value: Boolean) {
        prefs.edit().putBoolean("is_premium", value).apply()
        Timber.i("Premium status set to: $value")
    }

    // Called after successful Play Billing verification
    fun onPurchaseVerified(productId: String) {
        if (productId == PREMIUM_PRODUCT_ID) {
            setPremium(true)
        }
    }

    companion object {
        const val PREMIUM_PRODUCT_ID = "com.masterroot.premium"
    }
}
