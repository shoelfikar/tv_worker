package com.tvworker.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityHelper {

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val componentName = ComponentName(context, AdbAutoApproveService::class.java).flattenToString()
        return enabledServices.split(':').any {
            ComponentName.unflattenFromString(it)?.flattenToString() == componentName
        }
    }
}
