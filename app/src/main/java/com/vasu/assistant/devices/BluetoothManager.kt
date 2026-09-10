package com.vasu.assistant.devices

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BluetoothManager - Controls Bluetooth settings and intents.
 */
@Singleton
class BluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openBluetoothSettings(): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("bluetooth", "Opening Bluetooth settings")
        } catch (e: Exception) {
            ActionResult.error("bluetooth", "Failed to open Bluetooth settings", e.message ?: "UnknownError")
        }
    }
}
