package com.vasu.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Offline Speech Engine fallback for VASU Assistant.
 * Uses Android system TextToSpeech when offline or without internet connectivity.
 */
class OfflineSpeechEngine(private val context: Context) : VasuSpeechEngine {

    private val tag = "OfflineSpeechEngine"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speaking = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                // Prefer Hindi (India) or English (India)
                val hiLocale = Locale("hi", "IN")
                val langResult = tts?.setLanguage(hiLocale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.ENGLISH)
                }
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.1f)
                Log.d(tag, "Offline TextToSpeech initialized successfully")
            } else {
                Log.w(tag, "Offline TextToSpeech init failed with status: $status")
            }
        }
    }

    override fun speak(text: String, onStart: (() -> Unit)?, onEnd: (() -> Unit)?) {
        if (!isTtsReady || tts == null) {
            onEnd?.invoke()
            return
        }

        speaking = true
        val utteranceId = "VASU_OFFLINE_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                speaking = true
                onStart?.invoke()
            }

            override fun onDone(id: String?) {
                speaking = false
                onEnd?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                speaking = false
                onEnd?.invoke()
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    override fun stop() {
        speaking = false
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping TTS", e)
        }
    }

    override fun isSpeaking(): Boolean = speaking

    override fun release() {
        stop()
        try {
            tts?.shutdown()
            tts = null
            isTtsReady = false
        } catch (e: Exception) {
            Log.w(tag, "Error shutting down TTS", e)
        }
    }
}
