package com.vasu.assistant.core.context

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveContextReceiver @Inject constructor() {

    private val _contextState = MutableStateFlow(DeviceContext())
    val contextState: StateFlow<DeviceContext> = _contextState.asStateFlow()

    data class DeviceContext(
        val batteryLevel: Int = -1,
        val isCharging: Boolean = false,
        val isHeadsetPlugged: Boolean = false,
        val isAirplaneMode: Boolean = false,
        val ringerMode: String = "unknown",
        val isPowerSave: Boolean = false,
        val isScreenOn: Boolean = true,
        val isUserPresent: Boolean = true,
        val connectedBluetoothDevice: String? = null,
        val lastEvent: String = "",
        val lastEventTime: Long = 0
    )

    fun register(context: Context) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction("android.media.RINGER_MODE_CHANGED")
            addAction("android.os.action.POWER_SAVE_MODE_CHANGED")
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        context.registerReceiver(receiver, filter)
        Log.d(TAG, "Proactive context receiver registered")
    }

    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(receiver)
            Log.d(TAG, "Proactive context receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Unregister error: ${e.message}")
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    val percentage = if (level >= 0) (level * 100) / scale else -1
                    updateState { it.copy(batteryLevel = percentage) }
                    emitEvent("battery_changed", mapOf("level" to percentage))
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    updateState { it.copy(isCharging = true) }
                    emitEvent("power_connected")
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    updateState { it.copy(isCharging = false) }
                    emitEvent("power_disconnected")
                }
                Intent.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", 0)
                    updateState { it.copy(isHeadsetPlugged = state == 1) }
                    emitEvent("headset_plug", mapOf("plugged" to (state == 1)))
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val enabled = intent.getBooleanExtra("state", false)
                    updateState { it.copy(isAirplaneMode = enabled) }
                    emitEvent("airplane_mode", mapOf("enabled" to enabled))
                }
                "android.media.RINGER_MODE_CHANGED" -> {
                    val mode = intent.getIntExtra("android.media.EXTRA_RINGER_MODE", -1)
                    val modeName = when (mode) {
                        0 -> "silent"
                        1 -> "vibrate"
                        2 -> "normal"
                        else -> "unknown"
                    }
                    updateState { it.copy(ringerMode = modeName) }
                    emitEvent("ringer_changed", mapOf("mode" to modeName))
                }
                "android.os.action.POWER_SAVE_MODE_CHANGED" -> {
                    val bManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    val isPowerSave = bManager?.isPowerSaveMode ?: false
                    updateState { it.copy(isPowerSave = isPowerSave) }
                    emitEvent("power_save", mapOf("enabled" to isPowerSave))
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val name = device?.name ?: "Unknown"
                    updateState { it.copy(connectedBluetoothDevice = name) }
                    emitEvent("bluetooth_connected", mapOf("device" to name))
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    updateState { it.copy(connectedBluetoothDevice = null) }
                    emitEvent("bluetooth_disconnected")
                }
                Intent.ACTION_SCREEN_ON -> {
                    updateState { it.copy(isScreenOn = true) }
                    emitEvent("screen_on")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    updateState { it.copy(isScreenOn = false) }
                    emitEvent("screen_off")
                }
                Intent.ACTION_USER_PRESENT -> {
                    updateState { it.copy(isUserPresent = true) }
                    emitEvent("user_present")
                }
            }
        }
    }

    private fun updateState(transform: (DeviceContext) -> DeviceContext) {
        _contextState.value = transform(_contextState.value)
    }

    private fun emitEvent(type: String, properties: Map<String, Any> = emptyMap()) {
        val event = ContextEvent(type, properties, System.currentTimeMillis())
        _contextState.value = _contextState.value.copy(
            lastEvent = type,
            lastEventTime = event.timestamp
        )
        Log.d(TAG, "Context event: $type $properties")

        // Track with telemetry
        try {
            val telemetry = Class.forName("com.vasu.assistant.core.telemetry.TelemetryManager")
            val instance = telemetry.getDeclaredMethod("getInstance").invoke(null)
            telemetry.getDeclaredMethod("track", String::class.java, String::class.java, Map::class.java)
                .invoke(instance, "context", type, properties)
        } catch (_: Exception) {}
    }

    fun getCurrentContext(): DeviceContext = _contextState.value

    fun getBatteryLevel(): Int = _contextState.value.batteryLevel
    fun isCharging(): Boolean = _contextState.value.isCharging
    fun isHeadsetPlugged(): Boolean = _contextState.value.isHeadsetPlugged
    fun isAirplaneMode(): Boolean = _contextState.value.isAirplaneMode
    fun getRingerMode(): String = _contextState.value.ringerMode
    fun isPowerSaveMode(): Boolean = _contextState.value.isPowerSave
    fun isScreenOn(): Boolean = _contextState.value.isScreenOn
    fun getBluetoothDevice(): String? = _contextState.value.connectedBluetoothDevice

    data class ContextEvent(
        val type: String,
        val properties: Map<String, Any>,
        val timestamp: Long
    )

    companion object {
        private const val TAG = "ProactiveContext"
    }
}
