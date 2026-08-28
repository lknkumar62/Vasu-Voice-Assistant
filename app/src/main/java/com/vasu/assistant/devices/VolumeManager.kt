package com.vasu.assistant.devices

import android.content.Context
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun setVolume(level: Int): ActionResult {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val clampedLevel = level.coerceIn(0, 100)
        val androidLevel = (clampedLevel * maxVol / 100)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, androidLevel, 0)
        return ActionResult.success("volume", "Volume set to $clampedLevel%")
    }

    fun getVolume(): Int {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (currentVol * 100 / maxVol)
    }

    fun volumeUp(): ActionResult {
        val current = getVolume()
        val newLevel = minOf(current + 10, 100)
        return setVolume(newLevel)
    }

    fun volumeDown(): ActionResult {
        val current = getVolume()
        val newLevel = maxOf(current - 10, 0)
        return setVolume(newLevel)
    }

    fun setRingVolume(level: Int): ActionResult {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        val clampedLevel = level.coerceIn(0, 100)
        val androidLevel = (clampedLevel * maxVol / 100)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, androidLevel, 0)
        return ActionResult.success("ring_volume", "Ring volume set to $clampedLevel%")
    }

    fun setAlarmVolume(level: Int): ActionResult {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val clampedLevel = level.coerceIn(0, 100)
        val androidLevel = (clampedLevel * maxVol / 100)
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, androidLevel, 0)
        return ActionResult.success("alarm_volume", "Alarm volume set to $clampedLevel%")
    }
}
