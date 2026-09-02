package com.vasu.assistant.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTSManager - Text-to-Speech engine wrapper implementing [HindiSpeechService].
 *
 * Features:
 * - Clean HindiSpeechService implementation for natural Hindi Devanagari speech.
 * - Custom local VASU voice engine integration.
 * - Prioritizes Hindi (hi-IN) and offline Hindi-capable voices.
 * - Voice profiles (pitch, rate, volume) with persisted settings.
 * - Speech queue management and non-overlapping sequential playback.
 * - Utterance callbacks and non-blocking asynchronous lifecycle.
 * - Graceful fallback to default/English voice without crashing if Hindi voice missing.
 */
@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechQueue: SpeechQueue,
    private val settings: VasuSettings,
    private val customVoiceEngine: CustomVoiceEngine
) : HindiSpeechService {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false
    private var isPaused = false
    private var lastSpokenText: String = ""

    // State
    private val _state = MutableStateFlow(TTSState.IDLE)
    override val state: StateFlow<TTSState> = _state.asStateFlow()

    // Current voice profile
    private val _currentProfile = MutableStateFlow(settings.voiceProfile.value)
    val currentProfile: StateFlow<VoiceProfile> = _currentProfile.asStateFlow()

    // TTS Events
    private val _events = MutableSharedFlow<TTSEvent>(replay = 1)
    val events: SharedFlow<TTSEvent> = _events.asSharedFlow()

    // Speaking state
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Queue size
    val queueSize: StateFlow<Int> = speechQueue.queueSize

    // Available languages
    private val _availableLanguages = MutableStateFlow<List<Locale>>(emptyList())
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    // Available voices list
    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    override val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    // Whether VASU got a female voice, or engine default
    private val _voiceStatus = MutableStateFlow(VoiceStatus())
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    // Custom local voice model status
    val customVoiceStatus: StateFlow<VoiceModelStatus> = customVoiceEngine.status

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    /**
     * Initialize TTS engine asynchronously.
     */
    override fun initialize(profile: VoiceProfile, onReady: ((Boolean) -> Unit)?) {
        customVoiceEngine.detectCustomVoiceAssets()

        if (isInitialized && textToSpeech != null) {
            applyProfile(profile)
            onReady?.invoke(true)
            return
        }

        _state.value = TTSState.INITIALIZING

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _state.value = TTSState.READY

                // Apply voice profile (defaulting to Hindi hi-IN)
                val applied = applyProfile(profile)

                // Detect available languages
                detectLanguages()

                onReady?.invoke(applied)
            } else {
                _state.value = TTSState.ERROR
                val err = "Local TextToSpeech initialization failed (code $status)"
                Log.e(TAG, err)
                _events.tryEmit(TTSEvent.Error(err))
                onReady?.invoke(false)
            }
        }

        // Set utterance listener
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _state.value = TTSState.SPEAKING
                val text = utteranceId ?: ""
                _events.tryEmit(TTSEvent.SpeakingStarted(text))
                onStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.READY
                val text = utteranceId ?: ""
                _events.tryEmit(TTSEvent.SpeakingCompleted(text))
                onDoneCallback?.invoke()

                // Process next in queue
                processNextInQueue()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                val err = "TTS utterance error"
                _events.tryEmit(TTSEvent.Error(err))
                onErrorCallback?.invoke(err)
                processNextInQueue()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                val err = "TTS error code: $errorCode"
                _events.tryEmit(TTSEvent.Error(err))
                onErrorCallback?.invoke(err)
                processNextInQueue()
            }
        })
    }

    /**
     * Backward-compatible overload for initialize().
     */
    fun initialize() {
        initialize(settings.voiceProfile.value, null)
    }

    override fun isAvailable(): Boolean = isInitialized && textToSpeech != null

    override fun isOfflineVoiceAvailable(): Boolean {
        val tts = textToSpeech ?: return false
        val voices = runCatching { tts.voices }.getOrNull() ?: return false
        return voices.any { voice ->
            voice.locale.language == "hi" && !voice.isNetworkConnectionRequired
        }
    }

    /**
     * Speak text immediately, abandoning anything already queued.
     */
    override fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        speechQueue.clear()
        this.onStartCallback = onStart
        this.onDoneCallback = onDone
        this.onErrorCallback = onError
        this.lastSpokenText = text
        this.isPaused = false

        // 1. Try local custom voice sample first if available
        if (customVoiceEngine.hasCustomSampleFor(text)) {
            _isSpeaking.value = true
            _state.value = TTSState.SPEAKING
            onStart?.invoke()
            val played = customVoiceEngine.playCustomSample(text) {
                _isSpeaking.value = false
                _state.value = TTSState.READY
                onDone?.invoke()
                processNextInQueue()
            }
            if (played) return
        }

        if (!isInitialized) {
            initialize(settings.voiceProfile.value) { success ->
                if (success) {
                    speechQueue.enqueue(text, priority = true)
                    processNextInQueue()
                } else {
                    onError?.invoke("Local TTS unavailable on device")
                }
            }
            return
        }

        utter(text)
    }

    /**
     * Speak text immediately (convenience overload).
     */
    fun speak(text: String) {
        speak(text, null, null, null)
    }

    /**
     * Add text to queue (plays sequentially without overlap).
     */
    fun speakQueued(text: String) {
        if (customVoiceEngine.hasCustomSampleFor(text) && !_isSpeaking.value) {
            speak(text)
            return
        }

        if (!isInitialized) {
            initialize(settings.voiceProfile.value) {
                speechQueue.enqueue(text)
                if (!_isSpeaking.value) {
                    processNextInQueue()
                }
            }
            return
        }

        if (_isSpeaking.value) {
            speechQueue.enqueue(text)
        } else {
            utter(text)
        }
    }

    /**
     * Hands one utterance to the engine after normalising for natural speech.
     */
    private fun utter(text: String) {
        val spoken = toSpeakableText(text)
        if (spoken.isBlank()) {
            onDoneCallback?.invoke()
            return
        }

        val tts = textToSpeech ?: run {
            onErrorCallback?.invoke("TextToSpeech not ready")
            return
        }

        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, _currentProfile.value.volume)
        }

        // Stop prior speech before starting to guarantee no overlap
        tts.stop()

        val utteranceId = spoken
        val result = tts.speak(
            spoken,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )

        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS speak failed with code: $result")
            _state.value = TTSState.ERROR
            onErrorCallback?.invoke("TTS speak error code $result")
        }
    }

    /**
     * Stop current speech and clear speech queue.
     */
    override fun stop() {
        customVoiceEngine.stop()
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        _isSpeaking.value = false
        isPaused = false
        speechQueue.clear()
        if (isInitialized) {
            _state.value = TTSState.READY
        }
    }

    /**
     * Pause speech.
     */
    override fun pause() {
        customVoiceEngine.stop()
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error pausing TTS", e)
        }
        _isSpeaking.value = false
        isPaused = true
        _state.value = TTSState.PAUSED
    }

    /**
     * Resume speech if paused.
     */
    override fun resume() {
        if (isPaused && lastSpokenText.isNotBlank()) {
            isPaused = false
            speak(lastSpokenText, onStartCallback, onDoneCallback, onErrorCallback)
        }
    }

    /**
     * Release TTS resources.
     */
    override fun release() {
        shutdown()
    }

    /**
     * Apply voice profile (configuring locale, pitch, rate, offline Hindi voice).
     */
    override fun applyProfile(profile: VoiceProfile): Boolean {
        _currentProfile.value = profile

        val tts = textToSpeech ?: return false
        tts.setPitch(profile.pitch.coerceIn(VasuSettings.PITCH_RANGE))
        tts.setSpeechRate(profile.speechRate.coerceIn(VasuSettings.RATE_RANGE))

        val targetLocale = profile.language.toLocale()
        val result = tts.setLanguage(targetLocale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "${profile.language} voice is not installed, falling back to US English")
            tts.setLanguage(Locale.US)
            selectBestVoice(tts, Locale.US)
            _events.tryEmit(TTSEvent.Error("${profile.language} voice is not installed - using English fallback"))
            return false
        }

        selectBestVoice(tts, targetLocale)
        return true
    }

    private fun selectBestVoice(tts: TextToSpeech, target: Locale) {
        val voices = runCatching { tts.voices }.getOrNull() ?: emptySet()
        if (voices.isEmpty()) {
            _voiceStatus.value = VoiceStatus(VoiceGender.NO_VOICES)
            return
        }

        _availableVoices.value = voices.map { it.name }

        val candidates = voices
            .filter { it.locale.language == target.language }
            .ifEmpty { voices.filter { it.locale.language == Locale.US.language } }

        // Select offline female first, then offline voice, then female, then first available
        val bestVoice = candidates
            .sortedWith(
                compareBy(
                    { it.isNetworkConnectionRequired },
                    { !isFemaleVoiceName(it.name) },
                    { -it.quality }
                )
            )
            .firstOrNull()

        if (bestVoice == null) {
            _voiceStatus.value = VoiceStatus(VoiceGender.UNLABELLED)
            return
        }

        val success = tts.setVoice(bestVoice) == TextToSpeech.SUCCESS
        val isFemale = isFemaleVoiceName(bestVoice.name)

        _voiceStatus.value = if (success && isFemale) {
            VoiceStatus(VoiceGender.FEMALE, bestVoice.name)
        } else if (success) {
            VoiceStatus(VoiceGender.UNLABELLED, bestVoice.name)
        } else {
            VoiceStatus(VoiceGender.UNLABELLED)
        }
    }

    private fun String.toLocale(): Locale {
        val parts = split('-', '_')
        return when {
            parts.size >= 2 -> Locale(parts[0], parts[1])
            parts.size == 1 && parts[0].isNotBlank() -> Locale(parts[0])
            else -> Locale("hi", "IN")
        }
    }

    fun setLanguage(locale: Locale) {
        textToSpeech?.let { tts ->
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                _events.tryEmit(TTSEvent.Error("Language not supported: ${locale.displayLanguage}"))
            } else {
                selectBestVoice(tts, locale)
            }
        }
    }

    fun isSpeaking(): Boolean = _isSpeaking.value || customVoiceEngine.isPlaying()

    fun shutdown() {
        customVoiceEngine.stop()
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
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

            _availableLanguages.value = languages
        }
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
