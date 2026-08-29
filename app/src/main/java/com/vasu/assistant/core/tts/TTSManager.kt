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
 * - Hindi + English support
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
    private val settings: VasuSettings
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

    /**
     * Initialize TTS engine. Defaults to the profile the user last saved, so a
     * chosen rate/pitch/language survives a process restart.
     */
    fun initialize(profile: VoiceProfile = settings.voiceProfile.value) {
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
        if (!isInitialized) {
            initialize()
            speechQueue.enqueue(text, priority = true)
            return
        }

        speechQueue.clear()
        utter(text)
    }

    /**
     * Add text to queue (plays after current)
     */
    fun speakQueued(text: String) {
        if (!isInitialized) {
            initialize()
            speechQueue.enqueue(text)
            return
        }

        if (_isSpeaking.value) {
            // Currently speaking, add to queue
            speechQueue.enqueue(text)
        } else {
            utter(text)
        }
    }

    /**
     * Hands one utterance to the engine. Deliberately does not touch the queue:
     * draining the queue used to go through speak(), which cleared it, so only the
     * first queued line was ever spoken and the rest vanished.
     */
    private fun utter(text: String) {
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _currentProfile.value.volume)
        }

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            text // utteranceId = text for tracking
        )
    }

    /**
     * Stop current speech
     */
    fun stop() {
        textToSpeech?.stop()
        _isSpeaking.value = false
        speechQueue.clear()
        if (isInitialized) {
            _state.value = TTSState.READY
        }
    }

    /**
     * Pause speech (API 21+)
     */
    fun pause() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            textToSpeech?.stop() // Android TTS doesn't have native pause
            _state.value = TTSState.PAUSED
        }
    }

    /**
     * Apply voice profile. Returns false when the requested language is not
     * installed, so the caller can tell the user instead of silently speaking
     * Hindi text with an English voice.
     */
    fun applyProfile(profile: VoiceProfile): Boolean {
        _currentProfile.value = profile

        val tts = textToSpeech ?: return false
        tts.setPitch(profile.pitch.coerceIn(VasuSettings.PITCH_RANGE))
        tts.setSpeechRate(profile.speechRate.coerceIn(VasuSettings.RATE_RANGE))

        // The profile carries an explicit language tag; the old code only looked at
        // isHindi, so choosing en-IN silently fell back to en-US.
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

    /**
     * VASU speaks as a girl, so prefer a voice the engine labels female.
     *
     * Only an explicit "female" marker in the voice name counts. Engines that hide
     * gender keep their default voice and report UNLABELLED, so Settings can say
     * "this engine does not label gender" rather than claim a female voice VASU
     * never got. Local voices win over network ones so she still speaks offline.
     */
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

    /**
     * Change language
     */
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

    /**
     * Check if TTS is speaking
     */
    fun isSpeaking(): Boolean = _isSpeaking.value

    /**
     * Get current text being spoken
     */
    fun getCurrentText(): String? {
        // TTS API doesn't expose current text directly
        return null
    }

    /**
     * Cleanup resources
     */
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isInitialized = false
        _state.value = TTSState.IDLE
        _isSpeaking.value = false
    }

    // Private methods

    private fun processNextInQueue() {
        val nextItem = speechQueue.dequeue()
        if (nextItem != null) {
            utter(nextItem.text)
        }
    }

    private fun detectLanguages() {
        textToSpeech?.let { tts ->
            val languages = mutableListOf<Locale>()

            // Check Hindi
            val hindiResult = tts.isLanguageAvailable(Locale("hi", "IN"))
            if (hindiResult >= TextToSpeech.LANG_AVAILABLE) {
                languages.add(Locale("hi", "IN"))
            }

            // Check English
            val englishResult = tts.isLanguageAvailable(Locale.US)
            if (englishResult >= TextToSpeech.LANG_AVAILABLE) {
                languages.add(Locale.US)
            }

            // Check Hinglish (just Hindi locale)
            if (languages.contains(Locale("hi", "IN"))) {
                languages.add(Locale("hi", "IN")) // Hinglish uses Hindi locale
            }

            _availableLanguages.value = languages
        }
    }
}
