package com.vasu.assistant.devices

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceControlManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val torchManager: TorchManager,
    private val volumeManager: VolumeManager,
    private val bluetoothManager: BluetoothManager,
    private val mediaManager: MediaManager
) {
    fun getBatteryInfo(): Map<String, Any> {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return mapOf("level" to -1, "isCharging" to false)
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = batteryManager.isCharging
        return mapOf(
            "level" to level,
            "isCharging" to isCharging
        )
    }

    fun setBrightness(level: Int): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("brightness", "Opening display settings (brightness: $level%)")
        } catch (e: Exception) {
            ActionResult.error("brightness", "Failed to set brightness", e.message ?: "Unknown")
        }
    }

    fun openSettings(): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("settings", "Opened settings")
        } catch (e: Exception) {
            ActionResult.error("settings", "Failed to open settings", e.message ?: "Unknown")
        }
    }

    fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "brand" to android.os.Build.BRAND,
            "model" to android.os.Build.MODEL,
            "android_version" to android.os.Build.VERSION.RELEASE,
            "sdk_version" to android.os.Build.VERSION.SDK_INT.toString()
        )
    }

    fun createAlarm(time: String, label: String = "VASU Alarm"): ActionResult {
        return try {
            val parts = time.trim().split(":", " ").filter { it.isNotBlank() }
            var hour = 7
            var minute = 0
            if (parts.isNotEmpty()) {
                hour = parts[0].toIntOrNull() ?: 7
            }
            if (parts.size > 1) {
                minute = parts[1].toIntOrNull() ?: 0
            }
            if (time.contains("PM", ignoreCase = true) && hour < 12) {
                hour += 12
            } else if (time.contains("AM", ignoreCase = true) && hour == 12) {
                hour = 0
            }

            val intent = Intent(android.provider.AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour)
                putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("create_alarm", "Alarm set for ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            ActionResult.error("create_alarm", "Failed to set alarm", e.message ?: "Unknown")
        }
    }

    fun setTimer(seconds: Int, label: String = "VASU Timer"): ActionResult {
        return try {
            val intent = Intent(android.provider.AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(android.provider.AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label)
                putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("set_timer", "Timer set for $seconds seconds")
        } catch (e: Exception) {
            ActionResult.error("set_timer", "Failed to set timer", e.message ?: "Unknown")
        }
    }

    fun toggleWifi(): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("wifi", "Opening Wi-Fi settings")
        } catch (e: Exception) {
            ActionResult.error("wifi", "Failed to toggle Wi-Fi", e.message ?: "Unknown")
        }
    }

    fun setRingerMode(mode: String): ActionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                ?: return ActionResult.error("ringer", "Audio manager unavailable", "No audio service")
            when (mode.lowercase()) {
                "silent", "mute" -> audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
                "vibrate" -> audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_VIBRATE
                else -> audioManager.ringerMode = android.media.AudioManager.RINGER_MODE_NORMAL
            }
            ActionResult.success("ringer", "Ringer mode set to $mode")
        } catch (e: Exception) {
            ActionResult.error("ringer", "Failed to set ringer mode", e.message ?: "Unknown")
        }
    }

    fun listInstalledApps(query: String = ""): ActionResult {
        return try {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = pm.queryIntentActivities(mainIntent, 0)
                .map {
                    val label = it.loadLabel(pm).toString()
                    val pkg = it.activityInfo.packageName
                    mapOf("name" to label, "package" to pkg)
                }
                .filter {
                    query.isBlank() ||
                    (it["name"] as String).contains(query, ignoreCase = true) ||
                    (it["package"] as String).contains(query, ignoreCase = true)
                }
                .sortedBy { it["name"] as String }

            ActionResult.success("apps", "Found ${apps.size} installed apps", mapOf("apps" to apps))
        } catch (e: Exception) {
            ActionResult.error("apps", "Failed to list apps", e.message ?: "Unknown")
        }
    }

    fun getTorch() = torchManager
    fun getVolume() = volumeManager
    fun getBluetooth() = bluetoothManager
    fun getMedia() = mediaManager
}
