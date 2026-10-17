package com.vasu.assistant.core.settings

import android.content.Context
import android.content.SharedPreferences
import com.vasu.assistant.core.tts.VoiceProfile
import com.vasu.assistant.core.voice.GeminiLiveProtocol
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

    private val _animationsEnabled = MutableStateFlow(true)
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _preferOfflineStt = MutableStateFlow(false)
    val preferOfflineStt: StateFlow<Boolean> = _preferOfflineStt.asStateFlow()

    private val _otpProtectionEnabled = MutableStateFlow(true)
    val otpProtectionEnabled: StateFlow<Boolean> = _otpProtectionEnabled.asStateFlow()

    private val _offlineOnly = MutableStateFlow(false)
    val offlineOnly: StateFlow<Boolean> = _offlineOnly.asStateFlow()

    private val _androidFallbackTtsEnabled = MutableStateFlow(false)
    val androidFallbackTtsEnabled: StateFlow<Boolean> = _androidFallbackTtsEnabled.asStateFlow()

    private val _geminiTtsVoice = MutableStateFlow(DEFAULT_GEMINI_TTS_VOICE)
    val geminiTtsVoice: StateFlow<String> = _geminiTtsVoice.asStateFlow()

    private val _geminiTtsModel = MutableStateFlow(DEFAULT_GEMINI_TTS_MODEL)
    val geminiTtsModel: StateFlow<String> = _geminiTtsModel.asStateFlow()

    private val _geminiLiveVoice = MutableStateFlow(DEFAULT_GEMINI_LIVE_VOICE)
    val geminiLiveVoice: StateFlow<String> = _geminiLiveVoice.asStateFlow()

    private val _geminiLiveModel = MutableStateFlow(DEFAULT_GEMINI_LIVE_MODEL)
    val geminiLiveModel: StateFlow<String> = _geminiLiveModel.asStateFlow()

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
        _otpProtectionEnabled.value = prefs.getBoolean("otp_protection_enabled", true)
        _offlineOnly.value = prefs.getBoolean("offline_only", false)
        _androidFallbackTtsEnabled.value = prefs.getBoolean("android_fallback_tts", false)
        _geminiTtsVoice.value = prefs.getString("gemini_tts_voice", DEFAULT_GEMINI_TTS_VOICE) ?: DEFAULT_GEMINI_TTS_VOICE
        _geminiTtsModel.value = prefs.getString("gemini_tts_model", DEFAULT_GEMINI_TTS_MODEL) ?: DEFAULT_GEMINI_TTS_MODEL
        _geminiLiveVoice.value = prefs.getString("gemini_live_voice", DEFAULT_GEMINI_LIVE_VOICE) ?: DEFAULT_GEMINI_LIVE_VOICE
        _geminiLiveModel.value = prefs.getString("gemini_live_model", DEFAULT_GEMINI_LIVE_MODEL) ?: DEFAULT_GEMINI_LIVE_MODEL
        _animationsEnabled.value = prefs.getBoolean("animations_enabled", true)
        _preferOfflineStt.value = prefs.getBoolean("prefer_offline_stt", false)
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

    fun setOtpProtectionEnabled(enabled: Boolean) {
        _otpProtectionEnabled.value = enabled
        prefs.edit().putBoolean("otp_protection_enabled", enabled).apply()
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

    fun setGeminiLiveVoice(voice: String) {
        _geminiLiveVoice.value = voice
        prefs.edit().putString("gemini_live_voice", voice).apply()
    }

    fun setGeminiLiveModel(model: String) {
        _geminiLiveModel.value = model
        prefs.edit().putString("gemini_live_model", model).apply()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled.value = enabled
        prefs.edit().putBoolean("animations_enabled", enabled).apply()
    }

    fun setPreferOfflineStt(enabled: Boolean) {
        _preferOfflineStt.value = enabled
        prefs.edit().putBoolean("prefer_offline_stt", enabled).apply()
    }

    companion object {
        const val DEFAULT_GEMINI_TTS_VOICE = "Kore"
        const val DEFAULT_GEMINI_TTS_MODEL = "gemini-3.1-flash-tts-preview"
        const val FALLBACK_GEMINI_TTS_MODEL = "gemini-2.5-flash-preview-tts"
        const val BASE_GEMINI_TTS_MODEL = "gemini-2.0-flash"

        // FIX-6: the Gemini Live (hands-free) voice and model are user-selectable.
        const val DEFAULT_GEMINI_LIVE_VOICE = DEFAULT_GEMINI_TTS_VOICE
        const val DEFAULT_GEMINI_LIVE_MODEL = GeminiLiveProtocol.LIVE_MODEL

        val GEMINI_LIVE_MODELS = listOf(
            GeminiLiveProtocol.LIVE_MODEL to "Gemini 3.8 Live (recommended)",
            GeminiLiveProtocol.LIVE_MODEL_FALLBACK to "Gemini 2.5 Live (native audio)"
        )

        // FIX-6: Jarvis (male) / Nova (female) voice-gender presets.
        const val VOICE_GENDER_MALE = "male"
        const val VOICE_GENDER_FEMALE = "female"
        const val JARVIS_GEMINI_VOICE = "Charon"
        const val NOVA_GEMINI_VOICE = "Kore"

        // Maya same-to-same voices — 43 voices across 3 personas (Friday/Maya/Venom)
        val MAYA_VOICES = listOf(
            // Friday persona (13) - friendly, sweet
            "friday_Aoede", "friday_Autonoe", "friday_Callirrhoe", "friday_Despina", "friday_Erinome", "friday_Gacrux", "friday_Kore", "friday_Laomedeia", "friday_Leda", "friday_Pulcherrima", "friday_Sulafat", "friday_Vindemiatrix", "friday_Zephyr",
            // Maya persona (13) - main, warm
            "maya_Aoede", "maya_Autonoe", "maya_Callirrhoe", "maya_Despina", "maya_Erinome", "maya_Gacrux", "maya_Kore", "maya_Laomedeia", "maya_Leda", "maya_Pulcherrima", "maya_Sulafat", "maya_Vindemiatrix", "maya_Zephyr",
            // Venom persona (17) - deep, powerful
            "venom_Achernar", "venom_Achird", "venom_Algenib", "venom_Algieba", "venom_Alnilam", "venom_Charon", "venom_Enceladus", "venom_Fenrir", "venom_Iapetus", "venom_Orus", "venom_Puck", "venom_Rasalgethi", "venom_Sadachbia", "venom_Sadaltager", "venom_Schedar", "venom_Umbriel", "venom_Zubenelgenubi"
        )

        // Gemini voice names (derived from Maya voices — suffix after _)
        val GEMINI_VOICES = listOf(
            "Kore", "Aoede", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Gacrux", "Laomedeia", "Leda", "Pulcherrima", "Sulafat", "Vindemiatrix", "Zephyr",
            "Achernar", "Achird", "Algenib", "Algieba", "Alnilam", "Charon", "Enceladus", "Fenrir", "Iapetus", "Orus", "Puck", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar", "Umbriel", "Zubenelgenubi"
        )

        // FIX-6: every catalog voice mapped to a gender so the Jarvis/Nova presets
        // can highlight a voice picked in the fine-grained selector as well.
        val MALE_GEMINI_VOICES = setOf(
            "Achird", "Algenib", "Algieba", "Alnilam", "Charon", "Enceladus", "Fenrir",
            "Iapetus", "Orus", "Puck", "Rasalgethi", "Sadachbia", "Sadaltager", "Schedar",
            "Umbriel", "Zubenelgenubi"
        )
        val FEMALE_GEMINI_VOICES = setOf(
            "Achernar", "Aoede", "Autonoe", "Callirrhoe", "Despina", "Erinome", "Gacrux",
            "Kore", "Laomedeia", "Leda", "Pulcherrima", "Sulafat", "Vindemiatrix", "Zephyr"
        )

        /** The voice the Jarvis/Nova gender presets select. */
        fun geminiVoiceForGender(gender: String): String =
            if (gender == VOICE_GENDER_MALE) JARVIS_GEMINI_VOICE else NOVA_GEMINI_VOICE

        /** Null when the voice is unknown (should not happen for catalog voices). */
        fun genderForVoice(voice: String): String? = when {
            voice in MALE_GEMINI_VOICES -> VOICE_GENDER_MALE
            voice in FEMALE_GEMINI_VOICES -> VOICE_GENDER_FEMALE
            else -> null
        }

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
