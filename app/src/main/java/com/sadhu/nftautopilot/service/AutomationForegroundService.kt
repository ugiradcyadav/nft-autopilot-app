package com.sadhu.nftautopilot.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sadhu.nftautopilot.NFTAutopilotApp
import com.sadhu.nftautopilot.MainActivity
import com.sadhu.nftautopilot.engine.automation.AutomationEngine
import com.sadhu.nftautopilot.engine.automation.AutomationState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@AndroidEntryPoint
class AutomationForegroundService : Service() {

    @Inject lateinit var automationEngine: AutomationEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val TAG = "AutomationService"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Automation engine starting…"))
        scope.launch {
            automationEngine.status.collect { status ->
                val text = when (status.state) {
                    AutomationState.RUNNING -> status.currentOperation?.let { "Running: $it" } ?: "Running — ${status.pendingJobCount} jobs pending"
                    AutomationState.PAUSED  -> "Paused — tap to resume"
                    AutomationState.ERROR   -> "Error: ${status.lastError}"
                    AutomationState.IDLE    -> "Idle"
                }
                updateNotification(text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START  -> {
                Log.i(TAG, "Starting automation engine")
                automationEngine.start()
            }
            ACTION_PAUSE  -> {
                Log.i(TAG, "Pausing automation engine")
                automationEngine.pause()
            }
            ACTION_STOP   -> {
                Log.i(TAG, "Stopping service")
                automationEngine.pause()
                stopSelf()
            }
        }
        return START_STICKY // Restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Requeue restart via AlarmManager when task is swiped away
        Log.w(TAG, "Task removed — service will attempt restart via START_STICKY")
        super.onTaskRemoved(rootIntent)
    }

    // ─── Notifications ────────────────────────────────────────────────────────

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val pauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AutomationForegroundService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, AutomationForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NFTAutopilotApp.CHANNEL_AUTOMATION)
            .setContentTitle("NFT Autopilot")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START = "com.sadhu.nftautopilot.ACTION_START"
        const val ACTION_PAUSE = "com.sadhu.nftautopilot.ACTION_PAUSE"
        const val ACTION_STOP  = "com.sadhu.nftautopilot.ACTION_STOP"
    }
}
