package com.vasu.assistant.core.tts

import java.util.Locale

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
 * How close VASU got to a female voice.
 *
 * Android's [android.speech.tts.Voice] carries no gender field, and Google's own
 * ids ("hi-in-x-hia-local") hide it in a triplet with no published mapping, so
 * decoding those would be a guess dressed up as a fact. UNLABELLED therefore
 * means "this engine does not say", not "this voice is male".
 */
enum class VoiceGender { UNKNOWN, FEMALE, UNLABELLED, NO_VOICES }

data class VoiceStatus(
    val gender: VoiceGender = VoiceGender.UNKNOWN,
    val voiceName: String? = null
)

private val FEMALE_VOICE_MARKERS = listOf("female", "woman", "girl", "-f-", "_f_")

/**
 * True only when the engine itself put a female marker in the voice name.
 *
 * Samsung ships "hi-IN-female"; some engines use "..._f_...". Google's
 * "hi-in-x-hia-local" style ids are deliberately not decoded: the triplet has no
 * published gender mapping, so a match would be a guess. Note "female" contains
 * "male", so a male check must never run first.
 */
internal fun isFemaleVoiceName(name: String): Boolean {
    val id = name.lowercase(Locale.ROOT)
    return FEMALE_VOICE_MARKERS.any { id.contains(it) }
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
