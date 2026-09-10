package com.vasu.assistant.voice

/**
 * Common speech engine interface for VASU Assistant.
 * Allows transparent switching between Online (Gemini Live native audio)
 * and Offline (local TTS) speech engines.
 */
interface VasuSpeechEngine {
    fun speak(text: String, onStart: (() -> Unit)? = null, onEnd: (() -> Unit)? = null)
    fun stop()
    fun isSpeaking(): Boolean
    fun release()
}
