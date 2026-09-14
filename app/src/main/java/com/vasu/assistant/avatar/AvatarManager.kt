package com.vasu.assistant.avatar

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.core.voice.GeminiVoiceState
import com.vasu.assistant.core.voice.VoiceStateManager
import com.vasu.assistant.ui.theme.VasuIdle
import com.vasu.assistant.ui.theme.VasuListening
import com.vasu.assistant.ui.theme.VasuSpeaking
import com.vasu.assistant.ui.theme.VasuThinking
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AvatarManager - bridges the 3D orb avatar to the existing voice pipeline.
 *
 * Single source of truth remains [VoiceStateManager.state]; no new event bus
 * is created. Callers observe the existing VoiceStateManager / TTSState flows
 * and forward into the onX() helpers below (or read [getCurrentState] /
 * [orbAnimation] / [orbColorHex] directly).
 *
 * Orb colors reuse the Maya palette single-sourced from ui.theme:
 * VasuListening (cyan), VasuSpeaking (purple), VasuThinking (yellow),
 * VasuIdle (dark grey).
 */
@Singleton
class AvatarManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceStateManager: VoiceStateManager
) {
    private val _orbAnimation = MutableStateFlow("idle_motion")
    val orbAnimation: StateFlow<String> = _orbAnimation.asStateFlow()

    private val _orbColorHex = MutableStateFlow(colorHex(VasuIdle))
    val orbColorHex: StateFlow<String> = _orbColorHex.asStateFlow()

    /** Transient emotional overlay (happy/sad/angry/surprised/error) on top of voice state. */
    @Volatile
    private var emotionOverride: String? = null

    /**
     * Delegates to [VoiceStateManager.state]. Returns an uppercase avatar key
     * compatible with [VasuAvatar.getStateMotion] (which lowercases before its
     * motionMap lookup): IDLE, LISTENING, THINKING, SPEAKING, HAPPY, SAD,
     * ANGRY, SURPRISED, ERROR.
     */
    fun getCurrentState(): String {
        emotionOverride?.let { return it }
        return mapVoiceState(voiceStateManager.state.value)
    }

    /** Pure mapper so existing collectors can forward VoiceStateManager values without a bus. */
    fun updateFromVoiceState(voiceState: GeminiVoiceState) {
        // Emotion overlays survive voice transitions until explicitly cleared
        // by onIdle()/onSpeaking()/onListening()/onThinking().
        if (emotionOverride != null) return
        when (voiceState) {
            GeminiVoiceState.SPEAKING -> onSpeaking()
            GeminiVoiceState.COMMAND_LISTENING,
            GeminiVoiceState.BACKGROUND_LISTENING,
            GeminiVoiceState.LISTENING,
            GeminiVoiceState.WAKE_DETECTED,
            GeminiVoiceState.CONNECTING,
            GeminiVoiceState.CONNECTED -> onListening()
            GeminiVoiceState.PROCESSING,
            GeminiVoiceState.THINKING -> onThinking()
            GeminiVoiceState.ERROR -> onError()
            GeminiVoiceState.IDLE,
            GeminiVoiceState.STOPPING,
            GeminiVoiceState.DISCONNECTED -> onIdle()
        }
    }

    /** Pure mapper so existing TTS collectors can forward TTSState values without a bus. */
    fun updateFromTtsState(ttsState: TTSState) {
        when (ttsState) {
            TTSState.SPEAKING -> onSpeaking()
            TTSState.PAUSED -> onThinking()
            TTSState.ERROR -> onError()
            TTSState.IDLE,
            TTSState.INITIALIZING,
            TTSState.READY -> {
                // Only fall back to idle when the voice pipeline is also idle;
                // otherwise keep whatever the VoiceStateManager-driven UI shows.
                if (!voiceStateManager.isSpeaking() &&
                    !voiceStateManager.isListening() &&
                    !voiceStateManager.isProcessing()
                ) {
                    onIdle()
                }
            }
        }
    }

    fun onSpeaking() {
        emotionOverride = null
        updateOrbAnimation("speaking")
        setOrbColor(colorHex(VasuSpeaking))
    }

    fun onListening() {
        emotionOverride = null
        updateOrbAnimation("listening")
        setOrbColor(colorHex(VasuListening))
    }

    fun onThinking() {
        emotionOverride = null
        updateOrbAnimation("thinking")
        setOrbColor(colorHex(VasuThinking))
    }

    fun onIdle() {
        emotionOverride = null
        updateOrbAnimation("idle")
        setOrbColor(colorHex(VasuIdle))
    }

    fun onHappy() {
        emotionOverride = "HAPPY"
        updateOrbAnimation("happy")
        setOrbColor(colorHex(VasuListening))
    }

    fun onSad() {
        emotionOverride = "SAD"
        updateOrbAnimation("sad")
        setOrbColor(colorHex(VasuIdle))
    }

    fun onAngry() {
        emotionOverride = "ANGRY"
        updateOrbAnimation("angry")
        setOrbColor(colorHex(VasuSpeaking))
    }

    fun onSurprised() {
        emotionOverride = "SURPRISED"
        updateOrbAnimation("surprised")
        setOrbColor(colorHex(VasuThinking))
    }

    fun onError() {
        emotionOverride = "ERROR"
        updateOrbAnimation("error")
        setOrbColor(colorHex(VasuSpeaking))
    }

    fun updateOrbAnimation(state: String) {
        val key = state.lowercase().removeSuffix("_motion")
        _orbAnimation.value = "${key}_motion"
    }

    fun setOrbColor(colorHex: String) {
        _orbColorHex.value = colorHex
    }

    private fun mapVoiceState(voiceState: GeminiVoiceState): String {
        return when (voiceState) {
            GeminiVoiceState.SPEAKING -> "SPEAKING"
            GeminiVoiceState.COMMAND_LISTENING,
            GeminiVoiceState.BACKGROUND_LISTENING,
            GeminiVoiceState.LISTENING,
            GeminiVoiceState.WAKE_DETECTED,
            GeminiVoiceState.CONNECTING,
            GeminiVoiceState.CONNECTED -> "LISTENING"
            GeminiVoiceState.PROCESSING,
            GeminiVoiceState.THINKING -> "THINKING"
            GeminiVoiceState.ERROR -> "ERROR"
            GeminiVoiceState.IDLE,
            GeminiVoiceState.STOPPING,
            GeminiVoiceState.DISCONNECTED -> "IDLE"
        }
    }

    companion object {
        private fun colorHex(color: androidx.compose.ui.graphics.Color): String {
            return String.format("#%08X", color.toArgb())
        }
    }
}
