package com.vasu.assistant.core.ai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VasuSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("vasu_settings", Context.MODE_PRIVATE)
    }

    private val _voiceProfile = MutableStateFlow(VoiceProfile())
    val voiceProfile: StateFlow<VoiceProfile> = _voiceProfile.asStateFlow()

    private val _wakeWordEnabled = MutableStateFlow(false)
    val wakeWordEnabled: StateFlow<Boolean> = _wakeWordEnabled.asStateFlow()

    private val _voiceGuardEnabled = MutableStateFlow(false)
    val voiceGuardEnabled: StateFlow<Boolean> = _voiceGuardEnabled.asStateFlow()

    private val _autoAllowEnabled = MutableStateFlow(true)
    val autoAllowEnabled: StateFlow<Boolean> = _autoAllowEnabled.asStateFlow()

    private val _offlineOnly = MutableStateFlow(false)
    val offlineOnly: StateFlow<Boolean> = _offlineOnly.asStateFlow()

    private val _androidFallbackTtsEnabled = MutableStateFlow(false)
    val androidFallbackTtsEnabled: StateFlow<Boolean> = _androidFallbackTtsEnabled.asStateFlow()

    private val _geminiTtsVoice = MutableStateFlow(DEFAULT_GEMINI_TTS_VOICE)
    val geminiTtsVoice: StateFlow<String> = _geminiTtsVoice.asStateFlow()

    private val _geminiTtsModel = MutableStateFlow(DEFAULT_GEMINI_TTS_MODEL)
    val geminiTtsModel: StateFlow<String> = _geminiTtsModel.asStateFlow()

    init {
        loadAll()
    }

    private fun loadAll() {
        _voiceProfile.value = VoiceProfile(
            language = prefs.getString("voice_language", "hi-IN") ?: "hi-IN",
            pitch = prefs.getFloat("voice_pitch", 1.0f),
            speechRate = prefs.getFloat("voice_speech_rate", 1.0f),
            volume = prefs.getFloat("voice_volume", 1.0f)
        )
        _wakeWordEnabled.value = prefs.getBoolean("wake_word_enabled", false)
        _voiceGuardEnabled.value = prefs.getBoolean("voice_guard_enabled", false)
        _autoAllowEnabled.value = prefs.getBoolean("auto_allow_enabled", true)
        _offlineOnly.value = prefs.getBoolean("offline_only", false)
        _androidFallbackTtsEnabled.value = prefs.getBoolean("android_fallback_tts", false)
        _geminiTtsVoice.value = prefs.getString("gemini_tts_voice", DEFAULT_GEMINI_TTS_VOICE) ?: DEFAULT_GEMINI_TTS_VOICE
        _geminiTtsModel.value = prefs.getString("gemini_tts_model", DEFAULT_GEMINI_TTS_MODEL) ?: DEFAULT_GEMINI_TTS_MODEL
    }

    fun setVoiceProfile(profile: VoiceProfile) {
        _voiceProfile.value = profile
        prefs.edit()
            .putString("voice_language", profile.language)
            .putFloat("voice_pitch", profile.pitch)
            .putFloat("voice_speech_rate", profile.speechRate)
            .putFloat("voice_volume", profile.volume)
            .apply()
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        _wakeWordEnabled.value = enabled
        prefs.edit().putBoolean("wake_word_enabled", enabled).apply()
    }

    fun setVoiceGuardEnabled(enabled: Boolean) {
        _voiceGuardEnabled.value = enabled
        prefs.edit().putBoolean("voice_guard_enabled", enabled).apply()
    }

    fun setAutoAllowEnabled(enabled: Boolean) {
        _autoAllowEnabled.value = enabled
        prefs.edit().putBoolean("auto_allow_enabled", enabled).apply()
    }

    fun setOfflineOnly(enabled: Boolean) {
        _offlineOnly.value = enabled
        prefs.edit().putBoolean("offline_only", enabled).apply()
    }

    fun setAndroidFallbackTtsEnabled(enabled: Boolean) {
        _androidFallbackTtsEnabled.value = enabled
        prefs.edit().putBoolean("android_fallback_tts", enabled).apply()
    }

    fun setGeminiTtsVoice(voice: String) {
        _geminiTtsVoice.value = voice
        prefs.edit().putString("gemini_tts_voice", voice).apply()
    }

    fun setGeminiTtsModel(model: String) {
        _geminiTtsModel.value = model
        prefs.edit().putString("gemini_tts_model", model).apply()
    }

    companion object {
        const val DEFAULT_GEMINI_TTS_VOICE = "Kore"
        const val DEFAULT_GEMINI_TTS_MODEL = "gemini-3.1-flash-tts-preview"
        const val FALLBACK_GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        const val BASE_GEMINI_TTS_MODEL = "gemini-2.0-flash"

        val LANGUAGES = listOf(
            "hi-IN" to "Hindi (India)",
            "en-IN" to "English (India)",
            "en-US" to "English (US)",
            "hi-Latn" to "Hinglish"
        )

        val RATE_RANGE = 0.5f..2.0f
        val PITCH_RANGE = 0.5f..2.0f
    }
}
