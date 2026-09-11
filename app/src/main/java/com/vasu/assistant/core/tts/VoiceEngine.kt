package com.vasu.assistant.core.tts

interface VoiceEngine {
    val engineName: String
    val isAvailable: Boolean
    suspend fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean
    fun stop()
}
