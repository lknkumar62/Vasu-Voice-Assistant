package com.vasu.assistant.devices

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun playPause(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        return ActionResult.success("media", "Play/Pause toggled")
    }

    fun play(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
        return ActionResult.success("media", "Playing")
    }

    fun pause(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        return ActionResult.success("media", "Paused")
    }

    fun next(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT)
        return ActionResult.success("media", "Next track")
    }

    fun previous(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        return ActionResult.success("media", "Previous track")
    }

    fun stop(): ActionResult {
        sendKeyEvent(KeyEvent.KEYCODE_MEDIA_STOP)
        return ActionResult.success("media", "Stopped")
    }

    fun isMusicActive(): Boolean = audioManager.isMusicActive

    private fun sendKeyEvent(keyCode: Int) {
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}
