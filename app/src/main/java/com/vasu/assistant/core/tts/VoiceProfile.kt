package com.vasu.assistant.core.tts

/**
 * Voice profile for TTS customization
 */
data class VoiceProfile(
    val name: String = "Default",
    val language: String = "hi-IN",
    val pitch: Float = 1.0f,        // 0.5f to 2.0f
    val speechRate: Float = 1.0f,    // 0.5f to 2.0f
    val volume: Float = 1.0f,        // 0.0f to 1.0f
    val isHindi: Boolean = true,
    val isEnglish: Boolean = true
) {
    companion object {
        // Preset profiles
        val VASU_DEFAULT = VoiceProfile(
            name = "VASU Default",
            language = "hi-IN",
            pitch = 1.0f,
            speechRate = 1.0f,
            isHindi = true,
            isEnglish = true
        )

        val VASU_HINDI = VoiceProfile(
            name = "VASU Hindi",
            language = "hi-IN",
            pitch = 1.0f,
            speechRate = 1.0f,
            isHindi = true,
            isEnglish = false
        )

        val VASU_ENGLISH = VoiceProfile(
            name = "VASU English",
            language = "en-US",
            pitch = 1.0f,
            speechRate = 1.0f,
            isHindi = false,
            isEnglish = true
        )

        val VASU_SPEED = VoiceProfile(
            name = "VASU Fast",
            language = "hi-IN",
            pitch = 1.0f,
            speechRate = 1.3f,
            isHindi = true,
            isEnglish = true
        )
    }
}

/**
 * TTS Engine state
 */
enum class TTSState {
    IDLE,
    INITIALIZING,
    READY,
    SPEAKING,
    PAUSED,
    ERROR
}

/**
 * TTS events for UI
 */
sealed class TTSEvent {
    data class SpeakingStarted(val text: String) : TTSEvent()
    data class SpeakingProgress(val text: String, val charIndex: Int, val charCount: Int) : TTSEvent()
    data class SpeakingCompleted(val text: String) : TTSEvent()
    data class Error(val message: String) : TTSEvent()
}
