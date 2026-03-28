package com.tx.terminal.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tx.terminal.MainActivity
import com.tx.terminal.R
import com.tx.terminal.native.NativeTerminal

/**
 * Foreground service for keeping terminal sessions alive
 * - Wake lock support
 * - Background execution
 * - Session persistence
 */
class TerminalService : Service() {

    private val binder = TerminalBinder()
    private var isRunning = false

    // Wake lock
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockEnabled = false

    companion object {
        private const val TAG = "TerminalService"
        private const val NOTIFICATION_CHANNEL_ID = "tx_terminal_channel"
        private const val NOTIFICATION_ID = 1
        private const val SERVICE_ID = 1001
        private const val WAKE_LOCK_TAG = "TXTerminal::WakeLock"
    }

    inner class TerminalBinder : Binder() {
        fun getService(): TerminalService = this@TerminalService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startForeground(SERVICE_ID, createNotification())
            isRunning = true
            Log.d(TAG, "Terminal service started")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        isRunning = false
        NativeTerminal.cleanup()
        Log.d(TAG, "Terminal service destroyed")
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "TX Terminal Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps terminal sessions running in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create the foreground notification
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("TX Terminal")
            .setContentText("Terminal session is active")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Acquire wake lock to keep terminal awake
     */
    fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            )
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout
            wakeLockEnabled = true
            Log.d(TAG, "Wake lock acquired")
        }
    }

    /**
     * Release wake lock
     */
    fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLockEnabled = false
            Log.d(TAG, "Wake lock released")
        }
    }

    /**
     * Set wake lock enabled/disabled
     */
    fun setWakeLockEnabled(enabled: Boolean) {
        if (enabled) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
    }

    /**
     * Check if wake lock is enabled
     */
    fun isWakeLockEnabled(): Boolean {
        return wakeLockEnabled && wakeLock?.isHeld == true
    }

    /**
     * Check if the service is running
     */
    fun isServiceRunning(): Boolean {
        return isRunning
    }
}
