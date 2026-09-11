package com.vasu.assistant.core.tts

import java.util.Locale

data class VoiceProfile(
    val language: String = "hi-IN",
    val isHindi: Boolean = true,
    val isEnglish: Boolean = false,
    val pitch: Float = 1.0f,
    val speechRate: Float = 1.0f,
    val volume: Float = 1.0f
) {
    companion object {
        val VASU_HINDI = VoiceProfile(language = "hi-IN", isHindi = true)
        val VASU_ENGLISH = VoiceProfile(language = "en-IN", isHindi = false, isEnglish = true)
        val VASU_DEFAULT = VASU_HINDI
    }

    fun locale(): Locale = if (isHindi) Locale("hi", "IN") else Locale("en", "IN")
}

data class VoiceStatus(
    val gender: VoiceGender = VoiceGender.UNKNOWN,
    val voiceName: String = "",
    val isOffline: Boolean = false,
    val quality: Int = 0
)

enum class VoiceGender { FEMALE, UNLABELLED, NO_VOICES, UNKNOWN }
