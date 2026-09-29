package com.masterroot.infrastructure.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.masterroot.R
import com.masterroot.domain.model.ConnectionStatus
import com.masterroot.infrastructure.adb.ConnectionManager
import com.masterroot.presentation.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import timber.log.Timber
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
// Notification Channel IDs
// ─────────────────────────────────────────────────────────────────────────────

object NotificationChannels {
    const val WIRELESS_SERVICE = "masterroot_wireless_service"
    const val ROOT_OPERATIONS  = "masterroot_root_operations"
    const val DEVICE_EVENTS    = "masterroot_device_events"
    const val ERRORS           = "masterroot_errors"

    fun createAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(listOf(
            NotificationChannel(WIRELESS_SERVICE, "Wireless Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "MASTER ROOT Wireless ADB persistent connection"
                setShowBadge(false)
            },
            NotificationChannel(ROOT_OPERATIONS, "Root Operations",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Active root operations status"
            },
            NotificationChannel(DEVICE_EVENTS, "Device Events",
                NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Device connection and detection events"
            },
            NotificationChannel(ERRORS, "Errors",
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Critical errors requiring attention"
            }
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WirelessAdbService — persistent foreground service for Wireless ADB
// ─────────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class WirelessAdbService : Service() {

    @Inject lateinit var connectionManager: ConnectionManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.masterroot.ACTION_STOP_WIRELESS"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, WirelessAdbService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WirelessAdbService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Timber.i("WirelessAdbService: stop requested by user")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Wireless ADB", "Connecting..."))
        observeConnectionStatus()
        return START_STICKY
    }

    private fun observeConnectionStatus() {
        scope.launch {
            connectionManager.activeConnection.collectLatest { status ->
                val notification = when (status) {
                    is ConnectionStatus.Connected -> {
                        val device = status.device
                        buildNotification(
                            title = "Wireless ADB  ● Connected",
                            text = "${device.manufacturer} ${device.model}"
                        )
                    }
                    is ConnectionStatus.Connecting ->
                        buildNotification("Wireless ADB", "Connecting...")
                    is ConnectionStatus.Lost ->
                        buildNotification("Wireless ADB  ○ Disconnected", status.reason)
                    is ConnectionStatus.Disconnected ->
                        buildNotification("Wireless ADB", "Disconnected")
                    is ConnectionStatus.Error ->
                        buildNotification("Wireless ADB  ✕ Error", "Connection error")
                }
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, WirelessAdbService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.WIRELESS_SERVICE)
            .setContentTitle("MASTER ROOT")
            .setContentText("$title\n$text")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .addAction(0, "OPEN", openIntent)
            .addAction(0, "STOP", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MasterRoot::WirelessAdbLock"
        ).also { it.acquire(10 * 60 * 1000L) } // max 10 minutes auto-release
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.release()
        super.onDestroy()
        Timber.i("WirelessAdbService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// ─────────────────────────────────────────────────────────────────────────────
// RootOperationService — foreground service during active root workflow
// ─────────────────────────────────────────────────────────────────────────────

@AndroidEntryPoint
class RootOperationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val NOTIFICATION_ID = 1002
        const val EXTRA_OPERATION = "operation"

        fun start(context: Context, operationDescription: String) {
            context.startForegroundService(
                Intent(context, RootOperationService::class.java).apply {
                    putExtra(EXTRA_OPERATION, operationDescription)
                }
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RootOperationService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val operation = intent?.getStringExtra(EXTRA_OPERATION) ?: "Root Operation"
        startForeground(NOTIFICATION_ID, buildOperationNotification(operation))
        return START_NOT_STICKY // Do not auto-restart — operation state would be inconsistent
    }

    private fun buildOperationNotification(operation: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, NotificationChannels.ROOT_OPERATIONS)
            .setContentTitle("MASTER ROOT — PROTECTED OPERATION ACTIVE")
            .setContentText(operation)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setSilent(false)
            .build()
    }

    fun updateNotification(operation: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildOperationNotification(operation))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MasterRoot::RootOperationLock"
        ).also { it.acquire(30 * 60 * 1000L) } // 30 minute max
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

// ─────────────────────────────────────────────────────────────────────────────
// BootReceiver
// ─────────────────────────────────────────────────────────────────────────────

class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.i("Boot completed — MASTER ROOT service not auto-started (manual launch required)")
            // We intentionally do NOT auto-start the wireless service on boot.
            // The user must open the app and explicitly connect.
        }
    }
}
