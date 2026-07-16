package com.tvworker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the foreground service after the device reboots.
 *
 * Note: the AccessibilityService itself is re-bound automatically by the
 * system on boot; this receiver additionally ensures our resilient
 * foreground process comes up so the app is guaranteed to be running.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("TvWorker", "Boot completed — starting foreground service")
            TvWorkerService.start(context)
        }
    }
}
