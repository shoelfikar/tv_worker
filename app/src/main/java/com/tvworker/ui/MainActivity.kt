package com.tvworker.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tvworker.TvWorkerApp
import com.tvworker.service.TvWorkerService
import com.tvworker.ui.navigation.AppNavigation
import com.tvworker.ui.theme.TvWorkerTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "POST_NOTIFICATIONS granted=$granted")
            // Start regardless: the foreground service still runs even if the
            // notification is suppressed.
            TvWorkerService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ensureNotificationPermissionAndStartService()

        val repository = TvWorkerApp.instance.repository

        setContent {
            TvWorkerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(
                        repository = repository,
                        onOpenAccessibilitySettings = { openAccessibilitySettings() }
                    )
                }
            }
        }
    }

    private fun ensureNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Launcher callback starts the service after the user responds.
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        TvWorkerService.start(this)
    }

    private fun openAccessibilitySettings() {
        val attempts = listOf(
            // 1. TV Settings with accessibility fragment (deep link)
            {
                startActivity(
                    Intent().apply {
                        component = ComponentName(
                            "com.android.tv.settings",
                            "com.android.tv.settings.MainSettings"
                        )
                        putExtra(":android:show_fragment", "com.android.tv.settings.accessibility.AccessibilityFragment")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            // 2. TV Settings main page (user navigates manually)
            {
                startActivity(
                    Intent().apply {
                        component = ComponentName(
                            "com.android.tv.settings",
                            "com.android.tv.settings.MainSettings"
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            },
            // 3. Generic settings fallback
            {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        )

        for ((index, attempt) in attempts.withIndex()) {
            try {
                attempt()
                Log.i(TAG, "Opened settings via attempt #${index + 1}")
                Toast.makeText(
                    this,
                    "Navigasi ke: Accessibility > TV Worker > Aktifkan",
                    Toast.LENGTH_LONG
                ).show()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Attempt #${index + 1} failed", e)
            }
        }

        Toast.makeText(
            this,
            "Tidak dapat membuka Settings",
            Toast.LENGTH_LONG
        ).show()
    }
}
