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
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
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

    fun getTorch() = torchManager
    fun getVolume() = volumeManager
    fun getBluetooth() = bluetoothManager
    fun getMedia() = mediaManager
}
