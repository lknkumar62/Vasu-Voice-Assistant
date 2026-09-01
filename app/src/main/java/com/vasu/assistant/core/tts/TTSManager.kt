package com.vasu.assistant.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTSManager - Text-to-Speech engine wrapper.
 *
 * Features:
 * - Custom local VASU voice engine integration
 * - Hindi + English + Hinglish support
 * - Voice profiles (pitch, rate, volume)
 * - Speech queue management
 * - Streaming text support
 * - Interruption handling
 * - Utterance callbacks
 */
@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechQueue: SpeechQueue,
    private val settings: VasuSettings,
    private val customVoiceEngine: CustomVoiceEngine
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // State
    private val _state = MutableStateFlow(TTSState.IDLE)
    val state: StateFlow<TTSState> = _state.asStateFlow()

    // Current voice profile
    private val _currentProfile = MutableStateFlow(settings.voiceProfile.value)
    val currentProfile: StateFlow<VoiceProfile> = _currentProfile.asStateFlow()

    // TTS Events
    private val _events = MutableSharedFlow<TTSEvent>(replay = 1)
    val events: SharedFlow<TTSEvent> = _events.asSharedFlow()

    // Speaking state
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Queue size
    val queueSize: StateFlow<Int> = speechQueue.queueSize

    // Available languages
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    // Whether VASU actually got a female voice, or only the engine default
    private val _voiceStatus = MutableStateFlow(VoiceStatus())
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    // Custom local voice model status
    val customVoiceStatus: StateFlow<VoiceModelStatus> = customVoiceEngine.status

    /**
     * Initialize TTS engine. Defaults to the profile the user last saved, so a
     * chosen rate/pitch/language survives a process restart.
     */
    fun initialize(profile: VoiceProfile = settings.voiceProfile.value) {
        customVoiceEngine.detectCustomVoiceAssets()

        if (isInitialized) return

        _state.value = TTSState.INITIALIZING

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _state.value = TTSState.READY

                // Apply voice profile
                applyProfile(profile)

                // Detect available languages
                detectLanguages()
            } else {
                _state.value = TTSState.ERROR
                _events.tryEmit(TTSEvent.Error("TTS initialization failed"))
            }
        }

        // Set utterance listener
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _state.value = TTSState.SPEAKING
                val text = utteranceId ?: ""
                _events.tryEmit(TTSEvent.SpeakingStarted(text))
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.READY
                val text = utteranceId ?: ""
                _events.tryEmit(TTSEvent.SpeakingCompleted(text))

                // Process next in queue
                processNextInQueue()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                _events.tryEmit(TTSEvent.Error("TTS utterance error"))
                processNextInQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                _events.tryEmit(TTSEvent.Error("TTS error code: $errorCode"))
                processNextInQueue()
            }
        })
    }

    /**
     * Speak text immediately, abandoning anything already queued.
     */
    fun speak(text: String) {
        speechQueue.clear()

        // 1. Try local custom voice sample first if available
        if (customVoiceEngine.hasCustomSampleFor(text)) {
            _isSpeaking.value = true
            _state.value = TTSState.SPEAKING
            val played = customVoiceEngine.playCustomSample(text) {
                _isSpeaking.value = false
                _state.value = TTSState.READY
                processNextInQueue()
            }
            if (played) return
        }

        if (!isInitialized) {
            initialize()
            speechQueue.enqueue(text, priority = true)
            return
        }

        utter(text)
    }

    /**
     * Add text to queue (plays after current)
     */
    fun speakQueued(text: String) {
        if (customVoiceEngine.hasCustomSampleFor(text) && !_isSpeaking.value) {
            speak(text)
            return
        }

        if (!isInitialized) {
            initialize()
            speechQueue.enqueue(text)
            return
        }

        if (_isSpeaking.value) {
            speechQueue.enqueue(text)
        } else {
            utter(text)
        }
    }

    /**
     * Hands one utterance to the engine.
     */
    private fun utter(text: String) {
        val spoken = toSpeakableText(text)
        if (spoken.isBlank()) return

        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _currentProfile.value.volume)
        }

        textToSpeech?.speak(
            spoken,
            TextToSpeech.QUEUE_FLUSH,
            params,
            spoken
        )
    }

    /**
     * Stop current speech
     */
    fun stop() {
        customVoiceEngine.stop()
        textToSpeech?.stop()
        _isSpeaking.value = false
        speechQueue.clear()
        if (isInitialized) {
            _state.value = TTSState.READY
        }
    }

    /**
     * Pause speech
     */
    fun pause() {
        textToSpeech?.stop()
        customVoiceEngine.stop()
        _state.value = TTSState.PAUSED
    }

    /**
     * Apply voice profile.
     */
    fun applyProfile(profile: VoiceProfile): Boolean {
        _currentProfile.value = profile

        val tts = textToSpeech ?: return false
        tts.setPitch(profile.pitch.coerceIn(VasuSettings.PITCH_RANGE))
        tts.setSpeechRate(profile.speechRate.coerceIn(VasuSettings.RATE_RANGE))

        val result = tts.setLanguage(profile.language.toLocale())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
            selectFemaleVoice(tts, Locale.US)
            _events.tryEmit(TTSEvent.Error("${profile.language} voice is not installed - using English"))
            return false
        }
        selectFemaleVoice(tts, profile.language.toLocale())
        return true
    }

    private fun selectFemaleVoice(tts: TextToSpeech, target: Locale) {
        val voices = runCatching { tts.voices }.getOrNull() ?: emptySet()
        if (voices.isEmpty()) {
            _voiceStatus.value = VoiceStatus(VoiceGender.NO_VOICES)
            return
        }

        val candidates = voices
            .filter { it.locale.language == target.language }
            .ifEmpty { voices.filter { it.locale.language == Locale.US.language } }

        val female = candidates
            .filter { isFemaleVoiceName(it.name) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality }))
            .firstOrNull()

        if (female == null) {
            _voiceStatus.value = VoiceStatus(VoiceGender.UNLABELLED)
            return
        }

        _voiceStatus.value = if (tts.setVoice(female) == TextToSpeech.SUCCESS) {
            VoiceStatus(VoiceGender.FEMALE, female.name)
        } else {
            VoiceStatus(VoiceGender.UNLABELLED)
        }
    }

    private fun String.toLocale(): Locale {
        val parts = split('-', '_')
        return when {
            parts.size >= 2 -> Locale(parts[0], parts[1])
            parts.size == 1 && parts[0].isNotBlank() -> Locale(parts[0])
            else -> Locale.US
        }
    }

    fun setLanguage(locale: Locale) {
        textToSpeech?.let { tts ->
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                _events.tryEmit(TTSEvent.Error("Language not supported: ${locale.displayLanguage}"))
            }
        }
    }

    fun isSpeaking(): Boolean = _isSpeaking.value || customVoiceEngine.isPlaying()

    fun shutdown() {
        customVoiceEngine.stop()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        _state.value = TTSState.IDLE
        _isSpeaking.value = false
    }

    private fun processNextInQueue() {
        val nextItem = speechQueue.dequeue()
        if (nextItem != null) {
            utter(nextItem.text)
        }
    }

    private fun detectLanguages() {
        textToSpeech?.let { tts ->
            val languages = mutableListOf<Locale>()

            val hindiResult = tts.isLanguageAvailable(Locale("hi", "IN"))
            if (hindiResult >= TextToSpeech.LANG_AVAILABLE) {
                languages.add(Locale("hi", "IN"))
            }

            val englishResult = tts.isLanguageAvailable(Locale.US)
            if (englishResult >= TextToSpeech.LANG_AVAILABLE) {
                languages.add(Locale.US)
            }

            if (languages.contains(Locale("hi", "IN"))) {
                languages.add(Locale("hi", "IN"))
            }

            _availableLanguages.value = languages
        }
    }
}
