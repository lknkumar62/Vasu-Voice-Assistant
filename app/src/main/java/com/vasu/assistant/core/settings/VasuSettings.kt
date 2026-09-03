package com.vasu.assistant.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.vasu.assistant.core.tts.VoiceProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persisted user preferences that are not secrets.
 *
 * The Settings screen previously held every toggle in Compose `remember` state, so
 * a change was lost the moment the user navigated away and no manager ever saw it.
 * This is the single source of truth instead: the UI observes the flows and the
 * managers read the same values, so a setting change actually alters behaviour.
 *
 * Credentials do not belong here — they live in SecureKeyStore, which is
 * Keystore-encrypted.
 */
@Singleton
class VasuSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _voiceProfile = MutableStateFlow(readVoiceProfile())
    val voiceProfile: StateFlow<VoiceProfile> = _voiceProfile.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(prefs.getBoolean(KEY_WAKE_WORD, false))
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _offlineOnly = MutableStateFlow(prefs.getBoolean(KEY_OFFLINE_ONLY, false))
    val offlineOnly: StateFlow<Boolean> = _offlineOnly.asStateFlow()

    private val _voiceGuardEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_GUARD, false))
    val voiceGuardEnabled: StateFlow<Boolean> = _voiceGuardEnabled.asStateFlow()

    private val _autoAllowEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_ALLOW, true))
    val autoAllowEnabled: StateFlow<Boolean> = _autoAllowEnabled.asStateFlow()

    fun setVoiceProfile(profile: VoiceProfile) {
        prefs.edit()
            .putString(KEY_TTS_LANGUAGE, profile.language)
            .putFloat(KEY_TTS_PITCH, profile.pitch)
            .putFloat(KEY_TTS_RATE, profile.speechRate)
            .putFloat(KEY_TTS_VOLUME, profile.volume)
            .apply()
        _voiceProfile.value = profile
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WAKE_WORD, enabled).apply()
        _wakeWordEnabled.value = enabled
    }

    fun setOfflineOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_ONLY, enabled).apply()
        _offlineOnly.value = enabled
    }

    fun setVoiceGuardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_GUARD, enabled).apply()
        _voiceGuardEnabled.value = enabled
    }

    fun setAutoAllowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ALLOW, enabled).apply()
        _autoAllowEnabled.value = enabled
    }

    private fun readVoiceProfile(): VoiceProfile {
        val language = prefs.getString(KEY_TTS_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
        return VoiceProfile(
            name = "Saved",
            language = language,
            pitch = prefs.getFloat(KEY_TTS_PITCH, 1.0f).coerceIn(PITCH_RANGE),
            speechRate = prefs.getFloat(KEY_TTS_RATE, 1.0f).coerceIn(RATE_RANGE),
            volume = prefs.getFloat(KEY_TTS_VOLUME, 1.0f).coerceIn(0f, 1f),
            isHindi = language.startsWith("hi"),
            isEnglish = language.startsWith("en")
        )
    }

    companion object {
        private const val FILE_NAME = "vasu_settings"
        private const val KEY_TTS_LANGUAGE = "tts_language"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_VOLUME = "tts_volume"
        private const val KEY_WAKE_WORD = "wake_word_enabled"
        private const val KEY_OFFLINE_ONLY = "offline_only"
        private const val KEY_VOICE_GUARD = "voice_guard_enabled"
        private const val KEY_AUTO_ALLOW = "auto_allow_enabled"

        const val DEFAULT_LANGUAGE = "hi-IN"

        /** Android TTS clamps outside these, so the UI must not offer more. */
        val PITCH_RANGE = 0.5f..2.0f
        val RATE_RANGE = 0.5f..2.0f

        val LANGUAGES = listOf(
            "hi-IN" to "Hindi (India)",
            "en-IN" to "English (India)",
            "en-US" to "English (US)"
        )
    }
}
