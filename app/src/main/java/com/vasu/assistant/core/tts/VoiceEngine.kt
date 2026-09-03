package com.vasu.assistant.core.tts

/**
 * VoiceEngine - Core abstraction for text-to-speech synthesis engines in VASU.
 *
 * Implemented by:
 * - [GeminiTtsEngine]: Online high-fidelity Hindi female assistant voice using Google Gemini TTS.
 * - [LocalTtsEngine]: Offline local engine using custom voice samples and on-device offline Hindi models.
 * - [AndroidFallbackTtsEngine]: Emergency last-resort fallback to Android system TTS (only if enabled).
 */
interface VoiceEngine {
    /** Distinct name identifying this engine in logs and diagnostics. */
    val engineName: String

    /** Whether this engine is currently configured and capable of synthesizing speech. */
    val isAvailable: Boolean

    /**
     * Synthesizes and plays the provided text.
     *
     * @param text The text to synthesize and speak.
     * @param onStart Invoked when audio playback starts.
     * @param onDone Invoked when audio playback finishes.
     * @param onError Invoked with technical reason if synthesis or playback fails.
     * @return true if synthesis/playback was successfully initiated or completed, false otherwise.
     */
    suspend fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean

    /** Stops current speech playback immediately and releases transient resources. */
    fun stop()
}
