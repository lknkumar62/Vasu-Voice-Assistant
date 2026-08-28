package com.vasu.assistant.core.stt

/**
 * Represents a speech recognition result from the STT engine.
 */
data class RecognitionResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
    val alternatives: List<AlternativeResult> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
) {
    data class AlternativeResult(
        val text: String,
        val confidence: Float
    )
}

/**
 * STT Engine state
 */
enum class STTState {
    IDLE,           // Not listening
    LISTENING,      // Actively listening
    PROCESSING,     // Processing audio
    RESULT_READY,   // Result available
    ERROR           // Error occurred
}

/**
 * STT Configuration
 */
data class STTConfig(
    val language: String = "hi-IN",         // Hindi (India) default
    val alternateLanguages: List<String> = listOf("en-US"),  // English fallback
    val partialResults: Boolean = true,      // Show partial results
    val maxResults: Int = 5,                 // Max alternative results
    val silenceTimeoutMs: Long = 5000,       // 5 seconds silence = stop
    val preferOffline: Boolean = false       // Prefer offline recognition
)
