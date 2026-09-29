package com.masterroot

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.masterroot.infrastructure.service.NotificationChannels
import com.masterroot.monetization.AdManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MasterRootApp : Application() {

    @Inject lateinit var adManager: AdManager

    override fun onCreate() {
        super.onCreate()

        // Logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Notification channels
        NotificationChannels.createAll(this)

        // AdMob — initialize after consent is established
        // We delay until the user has seen the consent dialog
        // adManager.initialize() is called from MainActivity after consent
        Timber.i("MASTER ROOT v${BuildConfig.VERSION_NAME} started")
    }
}
