package com.vasu.assistant.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class PermissionType {
    RUNTIME,
    OVERLAY,
    ACCESSIBILITY,
    NOTIFICATION_LISTENER,
    EXACT_ALARM,
    BATTERY_OPTIMIZATION
}

data class PermissionStatusItem(
    val id: String,
    val title: String,
    val description: String,
    val type: PermissionType,
    val permissionString: String? = null,
    val isGranted: Boolean = false,
    val settingsAction: String? = null
)

data class PermissionsUiState(
    val items: List<PermissionStatusItem> = emptyList(),
    val grantedCount: Int = 0,
    val totalCount: Int = 0,
    val lastRefresh: Long = System.currentTimeMillis()
)

@HiltViewModel
class PermissionsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    fun refreshPermissions(context: Context) {
        viewModelScope.launch {
            val list = mutableListOf<PermissionStatusItem>()

            // 1. Microphone (Core)
            val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "mic",
                    title = "Microphone Access",
                    description = "Required for voice commands, wake word detection, and audio processing",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.RECORD_AUDIO,
                    isGranted = hasMic
                )
            )

            // 2. Overlay Window (System Assistant Experience)
            val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
            list.add(
                PermissionStatusItem(
                    id = "overlay",
                    title = "Display Over Other Apps",
                    description = "Required to show the assistant popup when saying \"Hello Vasu\"",
                    type = PermissionType.OVERLAY,
                    isGranted = hasOverlay,
                    settingsAction = Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                )
            )

            // 3. Accessibility Service (Screen & Device Automation)
            val hasAccessibility = isAccessibilityServiceEnabled(context)
            list.add(
                PermissionStatusItem(
                    id = "accessibility",
                    title = "Screen Control (Accessibility)",
                    description = "Required for app automation, screen reading, typing, and clicking",
                    type = PermissionType.ACCESSIBILITY,
                    isGranted = hasAccessibility,
                    settingsAction = Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )

            // 4. Notification Listener
            val hasNotifListener = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
            list.add(
                PermissionStatusItem(
                    id = "notif_listener",
                    title = "Notification Access",
                    description = "Required to read incoming messages and perform notification actions",
                    type = PermissionType.NOTIFICATION_LISTENER,
                    isGranted = hasNotifListener,
                    settingsAction = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )
            )

            // 5. Post Notifications (Android 13+)
            val hasNotifPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
            list.add(
                PermissionStatusItem(
                    id = "post_notif",
                    title = "Post Notifications",
                    description = "Required for background service status and proactive alerts",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.POST_NOTIFICATIONS,
                    isGranted = hasNotifPost
                )
            )

            // 6. Phone Calls
            val hasCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "call",
                    title = "Make Phone Calls",
                    description = "Required to dial numbers and place voice calls directly",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.CALL_PHONE,
                    isGranted = hasCall
                )
            )

            // 7. Contacts
            val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "contacts",
                    title = "Read Contacts",
                    description = "Required to match spoken contact names for calls and messages",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.READ_CONTACTS,
                    isGranted = hasContacts
                )
            )

            // 8. SMS
            val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "sms",
                    title = "Send SMS",
                    description = "Required to send text messages via voice commands",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.SEND_SMS,
                    isGranted = hasSms
                )
            )

            // 9. Camera
            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "cam",
                    title = "Camera & Flashlight",
                    description = "Required for taking photos, QR scanning, and flashlight controls",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.CAMERA,
                    isGranted = hasCam
                )
            )

            // 10. Location
            val hasLoc = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            list.add(
                PermissionStatusItem(
                    id = "loc",
                    title = "Precise GPS Location",
                    description = "Required for nearby places, weather, and navigation commands",
                    type = PermissionType.RUNTIME,
                    permissionString = Manifest.permission.ACCESS_FINE_LOCATION,
                    isGranted = hasLoc
                )
            )

            // 11. Battery Optimization Exemption
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isIgnoringBattery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
            list.add(
                PermissionStatusItem(
                    id = "battery",
                    title = "Background Battery Exemption",
                    description = "Prevents Android OS from killing wake word background listening",
                    type = PermissionType.BATTERY_OPTIMIZATION,
                    isGranted = isIgnoringBattery,
                    settingsAction = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                )
            )

            val granted = list.count { it.isGranted }

            _uiState.value = PermissionsUiState(
                items = list,
                grantedCount = granted,
                totalCount = list.size,
                lastRefresh = System.currentTimeMillis()
            )
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(context.packageName)
    }

    fun openPermissionSettings(context: Context, item: PermissionStatusItem) {
        try {
            when (item.type) {
                PermissionType.OVERLAY -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
                PermissionType.ACCESSIBILITY -> {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
                PermissionType.NOTIFICATION_LISTENER -> {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
                PermissionType.BATTERY_OPTIMIZATION -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                    }
                }
                else -> {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
