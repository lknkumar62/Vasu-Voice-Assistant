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
 * Why recognition failed.
 *
 * SpeechRecognizer hands back a bare int, and the old code collapsed several very
 * different failures into lookalike sentences. In particular ERROR_CLIENT became
 * "Client error", which tells the user nothing and hid genuine problems such as
 * the recogniser never having been handed the microphone. Callers need the kind so
 * they can decide whether to retry, prompt for a permission, or ask the user to
 * speak up.
 */
enum class SttErrorKind {
    NO_SPEECH,             // Genuinely heard nothing
    MIC_PERMISSION_DENIED, // RECORD_AUDIO not granted
    MIC_BUSY,              // Another app, or our own previous session, holds the mic
    TIMEOUT,               // Speech started but never completed
    NETWORK_ERROR,         // Online recogniser unreachable
    RECOGNITION_ERROR,     // Engine ran but could not transcribe
    SERVICE_UNAVAILABLE,   // No recogniser installed, or it refused to start
    LANGUAGE_UNAVAILABLE,  // Requested locale unsupported or not downloaded
    AUDIO_ERROR,           // Capture hardware failure
    RATE_LIMITED,          // Too many requests (API 33+)
    UNKNOWN;

    /** Whether simply listening again could succeed. */
    val canRetry: Boolean
        get() = this == NO_SPEECH || this == TIMEOUT || this == MIC_BUSY ||
            this == NETWORK_ERROR || this == RECOGNITION_ERROR || this == RATE_LIMITED

    /** Whether the user must change something before retrying is worthwhile. */
    val needsUserAction: Boolean
        get() = this == MIC_PERMISSION_DENIED || this == SERVICE_UNAVAILABLE ||
            this == LANGUAGE_UNAVAILABLE
}

data class SttError(
    val kind: SttErrorKind,
    val message: String,
    val code: Int? = null
)

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
