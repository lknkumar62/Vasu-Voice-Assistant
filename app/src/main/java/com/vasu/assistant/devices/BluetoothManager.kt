package com.vasu.assistant.devices

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager as AndroidBluetoothManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? AndroidBluetoothManager
    private val bluetoothAdapter = bluetoothManager?.adapter

    fun isAvailable(): Boolean = bluetoothAdapter != null

    fun isEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun enable(): ActionResult {
        return if (!isAvailable()) {
            ActionResult.error("bluetooth", "Bluetooth not available", "No Bluetooth hardware")
        } else if (isEnabled()) {
            ActionResult.success("bluetooth", "Bluetooth is already on")
        } else {
            try {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.enable()
                ActionResult.success("bluetooth", "Bluetooth turned on")
            } catch (e: SecurityException) {
                // Open Bluetooth settings as fallback
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult.success("bluetooth", "Opening Bluetooth settings")
            }
        }
    }

    fun disable(): ActionResult {
        return if (!isAvailable()) {
            ActionResult.error("bluetooth", "Bluetooth not available", "No Bluetooth hardware")
        } else if (!isEnabled()) {
            ActionResult.success("bluetooth", "Bluetooth is already off")
        } else {
            try {
                @Suppress("DEPRECATION")
                bluetoothAdapter?.disable()
                ActionResult.success("bluetooth", "Bluetooth turned off")
            } catch (e: SecurityException) {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult.success("bluetooth", "Opening Bluetooth settings")
            }
        }
    }

    fun toggle(): ActionResult = if (isEnabled()) disable() else enable()

    fun getPairedDevices(): List<String> {
        return try {
            bluetoothAdapter?.bondedDevices?.map { it.name ?: "Unknown" } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }
}
