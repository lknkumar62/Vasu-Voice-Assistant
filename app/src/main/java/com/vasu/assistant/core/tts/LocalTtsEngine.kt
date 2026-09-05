package com.vasu.assistant.core.tts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocalTtsEngine - Handles on-device offline voice synthesis and pre-recorded audio playback.
 *
 * Capabilities:
 * - Direct playback of custom local audio samples in assets vasu_voice wav files (e.g. greeting, reply).
 * - Integration with local neural voice models (ONNX or TFLite) when bundled.
 * - Offline on-device Hindi voice synthesis via [AndroidHindiSpeechService] when available without internet.
 * - Does not claim arbitrary speech generation from standalone samples alone.
 */
@Singleton
class LocalTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customVoiceEngine: CustomVoiceEngine,
    private val androidSpeechService: AndroidHindiSpeechService
) : VoiceEngine {

    override val engineName: String = "LocalTtsEngine"

    override val isAvailable: Boolean
        get() = customVoiceEngine.status.value != VoiceModelStatus.ERROR || androidSpeechService.isAvailable()

    override suspend fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.Main) {
        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            onDone?.invoke()
            return@withContext true
        }

        // 1. Check if a pre-recorded custom voice sample matches this phrase
        if (customVoiceEngine.hasCustomSampleFor(speakable)) {
            Log.d(TAG, "Playing local custom voice sample for: \"$speakable\"")
            onStart?.invoke()
            val played = customVoiceEngine.playCustomSample(speakable) {
                onDone?.invoke()
            }
            if (played) return@withContext true
        }

        // 2. Check if a local neural model is active
        if (customVoiceEngine.status.value == VoiceModelStatus.ACTIVE_CUSTOM_MODEL) {
            Log.d(TAG, "Local custom neural model active, utilizing custom engine")
            onStart?.invoke()
            val played = customVoiceEngine.playCustomSample(speakable) {
                onDone?.invoke()
            }
            if (played) return@withContext true
        }

        // 3. Synthesize via on-device offline voice if available
        if (androidSpeechService.isOfflineVoiceAvailable()) {
            Log.d(TAG, "Synthesizing with on-device offline Hindi voice")
            androidSpeechService.speak(speakable, onStart, onDone, onError)
            return@withContext true
        }

        // 4. Synthesize via on-device Android TextToSpeech engine
        if (androidSpeechService.isAvailable()) {
            Log.d(TAG, "Synthesizing with Android system TextToSpeech")
            androidSpeechService.speak(speakable, onStart, onDone, onError)
            return@withContext true
        }

        // 5. If no TTS engine is ready on device
        Log.w(TAG, "No TTS voice or voice pack found for arbitrary phrase: \"$speakable\"")
        onError?.invoke("TTS not available on device")
        return@withContext false
    }

    override fun stop() {
        customVoiceEngine.stop()
        androidSpeechService.stop()
    }

    companion object {
        private const val TAG = "LocalTtsEngine"
    }
}
