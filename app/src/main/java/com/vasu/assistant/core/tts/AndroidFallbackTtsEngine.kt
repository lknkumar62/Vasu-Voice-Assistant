package com.vasu.assistant.core.tts

import android.util.Log
import com.vasu.assistant.core.settings.VasuSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AndroidFallbackTtsEngine - Emergency last-resort fallback to the Android system TTS.
 *
 * Rules:
 * - Only active if explicitly enabled by the user in settings ([VasuSettings.androidFallbackTtsEnabled]).
 * - NEVER silently replaces Gemini TTS or Local TTS.
 */
@Singleton
class AndroidFallbackTtsEngine @Inject constructor(
    private val settings: VasuSettings,
    private val androidSpeechService: AndroidHindiSpeechService
) : VoiceEngine {

    override val engineName: String = "AndroidFallbackTtsEngine"

    override val isAvailable: Boolean
        get() = settings.androidFallbackTtsEnabled.value && androidSpeechService.isAvailable()

    override suspend fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean = withContext(Dispatchers.Main) {
        if (!settings.androidFallbackTtsEnabled.value) {
            Log.d(TAG, "Android Fallback TTS is disabled by user preference")
            onError?.invoke("Android Fallback TTS is disabled")
            return@withContext false
        }

        Log.i(TAG, "Using Android system TTS as emergency fallback for: \"$text\"")
        androidSpeechService.speak(text, onStart, onDone, onError)
        return@withContext true
    }

    override fun stop() {
        androidSpeechService.stop()
    }

    companion object {
        private const val TAG = "AndroidFallbackTtsEngine"
    }
}
