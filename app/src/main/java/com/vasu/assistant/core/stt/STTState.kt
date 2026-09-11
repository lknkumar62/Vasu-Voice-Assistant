package com.vasu.assistant.core.stt

enum class STTState {
    IDLE,
    LISTENING,
    PROCESSING,
    RESULT_READY,
    ERROR
}

data class STTConfig(
    val language: String = "hi-IN",
    val partialResults: Boolean = true,
    val maxResults: Int = 5,
    val silenceTimeoutMs: Long = 3000
)

data class SttError(
    val kind: SttErrorKind,
    val message: String,
    val code: Int = -1
)

enum class SttErrorKind {
    NO_SPEECH,
    MIC_PERMISSION_DENIED,
    MIC_BUSY,
    TIMEOUT,
    NETWORK_ERROR,
    RECOGNITION_ERROR,
    SERVICE_UNAVAILABLE,
    LANGUAGE_UNAVAILABLE,
    AUDIO_ERROR,
    RATE_LIMITED,
    UNKNOWN
}

data class RecognitionResult(
    val text: String,
    val confidence: Float,
    val isFinal: Boolean,
    val alternatives: List<AlternativeResult> = emptyList()
) {
    data class AlternativeResult(val text: String, val confidence: Float)
}
