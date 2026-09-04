package com.vasu.assistant.core.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.vasu.assistant.accessibility.VasuAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SystemPermission(val displayName: String, val manifestString: String? = null) {
    MICROPHONE("Microphone", Manifest.permission.RECORD_AUDIO),
    CAMERA("Camera", Manifest.permission.CAMERA),
    LOCATION("Location", Manifest.permission.ACCESS_FINE_LOCATION),
    CALL_PHONE("Phone Calls", Manifest.permission.CALL_PHONE),
    SEND_SMS("Send SMS", Manifest.permission.SEND_SMS),
    READ_CONTACTS("Contacts", Manifest.permission.READ_CONTACTS),
    NOTIFICATIONS("Notifications", if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null),
    ACCESSIBILITY("Accessibility Service"),
    OVERLAY("Display Over Other Apps")
}

@Singleton
class SystemPermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun hasPermission(permission: SystemPermission): Boolean {
        return when (permission) {
            SystemPermission.ACCESSIBILITY -> {
                VasuAccessibilityService.isRunning.value || isAccessibilityEnabledInSettings()
            }
            SystemPermission.OVERLAY -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else true
            }
            SystemPermission.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            }
            else -> {
                val manifestStr = permission.manifestString ?: return true
                ContextCompat.checkSelfPermission(context, manifestStr) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    fun isAccessibilityEnabledInSettings(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }
}
