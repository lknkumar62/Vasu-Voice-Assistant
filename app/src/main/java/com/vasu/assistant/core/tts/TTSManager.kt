package com.vasu.assistant.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private val speechQueue: SpeechQueue
) {
    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    // State
    private val _state = MutableStateFlow(TTSState.IDLE)
    val state: StateFlow<TTSState> = _state.asStateFlow()

    // Current voice profile
    private val _currentProfile = MutableStateFlow(VoiceProfile.VASU_DEFAULT)
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

    /**
     * Initialize TTS engine
     */
    fun initialize(profile: VoiceProfile = VoiceProfile.VASU_DEFAULT) {
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
     * Speak text immediately (interrupts current)
     */
    fun speak(text: String) {
        if (!isInitialized) {
            initialize()
            speechQueue.enqueue(text, priority = true)
            return
        }

        // Stop current speech
        stop()

        // Speak the text
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
            // Not speaking, speak immediately
            speak(text)
        }
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
     * Apply voice profile
     */
    fun applyProfile(profile: VoiceProfile) {
        _currentProfile.value = profile

        textToSpeech?.let { tts ->
            tts.setPitch(profile.pitch)
            tts.setSpeechRate(profile.speechRate)

            // Try to set language
            val locale = if (profile.isHindi) Locale("hi", "IN") else Locale.US
            val result = tts.setLanguage(locale)

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                // Fallback to English
                tts.setLanguage(Locale.US)
            }
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
            speak(nextItem.text)
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
