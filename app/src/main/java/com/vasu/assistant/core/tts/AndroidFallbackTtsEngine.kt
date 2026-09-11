package com.vasu.assistant.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFallbackTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceEngine {

    override val engineName: String = "AndroidFallbackTts"
    override val isAvailable: Boolean get() = tts != null

    private var tts: TextToSpeech? = null
    private var ready = false

    fun initialize(onReady: ((Boolean) -> Unit)? = null) {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setLanguage(Locale("hi", "IN"))
            }
            onReady?.invoke(ready)
        }
    }

    override suspend fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean {
        val engine = tts
        if (engine == null || !ready) {
            onError?.invoke("Android fallback TTS not initialized")
            return false
        }

        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            onDone?.invoke()
            return true
        }

        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart?.invoke() }
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Deprecated") override fun onError(utteranceId: String?) { onError?.invoke("TTS error") }
            override fun onError(utteranceId: String?, errorCode: Int) { onError?.invoke("TTS error: $errorCode") }
        })

        val result = engine.speak(speakable, TextToSpeech.QUEUE_FLUSH, null, "vasu_fallback_${System.currentTimeMillis()}")
        if (result != TextToSpeech.SUCCESS) {
            onError?.invoke("TTS speak failed")
            return false
        }
        return true
    }

    override fun stop() {
        try { tts?.stop() } catch (e: Exception) { Log.w(TAG, "Error stopping fallback TTS", e) }
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) { Log.w(TAG, "Error shutting down fallback TTS", e) }
        tts = null
        ready = false
    }

    companion object {
        private const val TAG = "AndroidFallbackTtsEngine"
    }
}
