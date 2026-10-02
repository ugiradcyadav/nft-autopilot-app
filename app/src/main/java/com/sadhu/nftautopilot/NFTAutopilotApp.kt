package com.sadhu.nftautopilot

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import javax.inject.Inject

@HiltAndroidApp
class NFTAutopilotApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        instance = this
        installBouncyCastle()
        createNotificationChannels()
    }

    private fun installBouncyCastle() {
        // Android ships a stripped BC. web3j needs the full version for secp256k1.
        Security.removeProvider("BC")
        Security.addProvider(BouncyCastleProvider())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_AUTOMATION,
                    "Automation Engine",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "NFT Autopilot background automation" }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Mints, sales, errors, balance warnings" }
            )
        }
    }

    companion object {
        const val CHANNEL_AUTOMATION = "automation_engine"
        const val CHANNEL_ALERTS     = "nft_alerts"
        lateinit var instance: NFTAutopilotApp private set
    }
}
