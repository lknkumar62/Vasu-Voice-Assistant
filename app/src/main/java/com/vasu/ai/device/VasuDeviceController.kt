package com.vasu.ai.device

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.net.Uri

/**
 * Legitimate system controls with settings fallbacks where Android restricts direct mutation.
 */
class VasuDeviceController(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun volumeUp(): Boolean {
        audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun volumeDown(): Boolean {
        audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun mute(): Boolean {
        audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
        return true
    }

    fun openBrightnessSettings(): Boolean = open(Settings.ACTION_DISPLAY_SETTINGS)

    fun openWifiSettings(): Boolean = open(Settings.ACTION_WIFI_SETTINGS)

    fun openBluetoothSettings(): Boolean = open(Settings.ACTION_BLUETOOTH_SETTINGS)

    fun openDndSettings(): Boolean = open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun openAirplaneModeSettings(): Boolean = open(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    fun openBatterySaverSettings(): Boolean = open(Settings.ACTION_BATTERY_SAVER_SETTINGS)

    fun openLocationSettings(): Boolean = open(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    fun openDateTimeSettings(): Boolean = open(Settings.ACTION_DATE_SETTINGS)

    private fun open(action: String): Boolean = runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
