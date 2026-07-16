package com.tvworker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tvworker.R
import com.tvworker.ui.MainActivity

/**
 * Persistent foreground service that keeps the app process alive in the
 * background and surfaces a status notification. It does NOT perform the
 * auto-approve itself (that is the AccessibilityService's job) — its purpose
 * is resilience: a foreground process is far less likely to be killed by the
 * OS, and it lets us surface whether Accessibility is actually enabled.
 */
class TvWorkerService : Service() {

    companion object {
        private const val TAG = "TvWorkerService"
        private const val CHANNEL_ID = "tvworker_status"
        private const val NOTIFICATION_ID = 1

        /** Safely (re)start the foreground service from any context. */
        fun start(context: Context) {
            val intent = Intent(context, TvWorkerService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // e.g. ForegroundServiceStartNotAllowedException when triggered
                // from a restricted background state — ignore; other triggers
                // (boot receiver, activity) will bring it up.
                Log.w(TAG, "Could not start foreground service", e)
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundWithNotification()
        acquireWakeLock()
        Log.i(TAG, "Foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Accessibility status may have changed since last start — refresh it.
        updateNotification()
        // Ask the system to recreate us if we get killed.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val accessibilityOn = AccessibilityHelper.isAccessibilityEnabled(this)
        val statusText = if (accessibilityOn) {
            "Aktif — auto-approve siap"
        } else {
            "Accessibility OFF — buka app untuk mengaktifkan"
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TV Worker")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Status TV Worker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menunjukkan TV Worker berjalan di background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TvWorker::ServiceLock"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {
        }
        Log.i(TAG, "Foreground service destroyed")
    }
}
