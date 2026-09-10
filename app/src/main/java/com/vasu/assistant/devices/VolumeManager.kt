package com.vasu.assistant.devices

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VolumeManager - Controls stream audio volumes and muting.
 */
@Singleton
class VolumeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tag = "VolumeManager"
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    fun setVolume(percentage: Int): ActionResult {
        val am = audioManager ?: return ActionResult.error("volume", "AudioManager not available", "SERVICE_NOT_FOUND")
        return try {
            val clamped = percentage.coerceIn(0, 100)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val index = ((clamped / 100f) * max).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI)
            ActionResult.success("volume_set", "Volume set to $clamped%")
        } catch (e: Exception) {
            Log.e(tag, "Failed to set volume", e)
            ActionResult.error("volume", "Failed to change volume", e.message ?: "UnknownError")
        }
    }

    fun volumeUp(): ActionResult {
        val am = audioManager ?: return ActionResult.error("volume", "AudioManager not available", "SERVICE_NOT_FOUND")
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            ActionResult.success("volume_up", "Volume increased")
        } catch (e: Exception) {
            ActionResult.error("volume", "Failed to increase volume", e.message ?: "UnknownError")
        }
    }

    fun volumeDown(): ActionResult {
        val am = audioManager ?: return ActionResult.error("volume", "AudioManager not available", "SERVICE_NOT_FOUND")
        return try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            ActionResult.success("volume_down", "Volume decreased")
        } catch (e: Exception) {
            ActionResult.error("volume", "Failed to decrease volume", e.message ?: "UnknownError")
        }
    }

    fun getVolume(): Int {
        val am = audioManager ?: return 0
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        val curr = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((curr.toFloat() / max) * 100).toInt()
    }
}
