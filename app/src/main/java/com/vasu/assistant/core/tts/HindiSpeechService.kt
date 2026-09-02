package com.vasu.assistant.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clean service abstraction for Hindi Speech synthesis.
 *
 * Requirements:
 * - Asynchronous, non-blocking initialization.
 * - Prioritizes Hindi locale (hi-IN) and offline Hindi voices.
 * - Handles device-specific unavailable locales and voices gracefully without crashing.
 * - Provides lifecycle and control operations: initialize, speak, stop, pause, resume, release.
 * - Separates TTS mechanics from UI logic.
 */
interface HindiSpeechService {
    val state: StateFlow<TTSState>
    val isSpeaking: StateFlow<Boolean>
    val availableVoices: StateFlow<List<String>>

    fun initialize(profile: VoiceProfile = VoiceProfile.VASU_HINDI, onReady: ((Boolean) -> Unit)? = null)
    fun isAvailable(): Boolean
    fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    )
    fun stop()
    fun pause()
    fun resume()
    fun release()
    fun isOfflineVoiceAvailable(): Boolean
    fun applyProfile(profile: VoiceProfile): Boolean
}

@Singleton
class AndroidHindiSpeechService @Inject constructor(
    @ApplicationContext private val context: Context
) : HindiSpeechService {

    private var textToSpeech: TextToSpeech? = null
    private var isInitialized = false

    private val _state = MutableStateFlow(TTSState.IDLE)
    override val state: StateFlow<TTSState> = _state.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    override val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    private var currentProfile: VoiceProfile = VoiceProfile.VASU_HINDI
    private var lastSpokenText: String = ""
    private var isPaused: Boolean = false

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    override fun initialize(profile: VoiceProfile, onReady: ((Boolean) -> Unit)?) {
        if (isInitialized && textToSpeech != null) {
            applyProfile(profile)
            onReady?.invoke(true)
            return
        }

        currentProfile = profile
        _state.value = TTSState.INITIALIZING

        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                _state.value = TTSState.READY
                setupUtteranceListener()
                val configured = configureHindiEngine(profile)
                onReady?.invoke(configured)
            } else {
                _state.value = TTSState.ERROR
                Log.e(TAG, "Local TextToSpeech initialization failed with status: $status")
                onReady?.invoke(false)
            }
        }
    }

    override fun isAvailable(): Boolean = isInitialized && textToSpeech != null

    override fun isOfflineVoiceAvailable(): Boolean {
        val tts = textToSpeech ?: return false
        val voices = runCatching { tts.voices }.getOrNull() ?: return false
        return voices.any { voice ->
            voice.locale.language == "hi" && !voice.isNetworkConnectionRequired
        }
    }

    override fun speak(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            onDone?.invoke()
            return
        }

        this.onStartCallback = onStart
        this.onDoneCallback = onDone
        this.onErrorCallback = onError
        this.lastSpokenText = speakable
        this.isPaused = false

        if (!isAvailable()) {
            initialize(currentProfile) { success ->
                if (success) {
                    performSpeak(speakable)
                } else {
                    onError?.invoke("Hindi TTS unavailable on device")
                }
            }
            return
        }

        performSpeak(speakable)
    }

    private fun performSpeak(text: String) {
        val tts = textToSpeech ?: run {
            onErrorCallback?.invoke("TTS not initialized")
            return
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, currentProfile.volume)
        }

        // Always stop prior speech before beginning new response to avoid overlap
        tts.stop()

        val utteranceId = "vasu_utt_${System.currentTimeMillis()}"
        val result = tts.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            utteranceId
        )

        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS speak failed with code: $result")
            _state.value = TTSState.ERROR
            onErrorCallback?.invoke("Failed to synthesize speech")
        }
    }

    override fun stop() {
        try {
            textToSpeech?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        _isSpeaking.value = false
        isPaused = false
        if (isAvailable()) {
            _state.value = TTSState.READY
        }
    }

    override fun pause() {
        stop()
        isPaused = true
        _state.value = TTSState.PAUSED
    }

    override fun resume() {
        if (isPaused && lastSpokenText.isNotBlank()) {
            isPaused = false
            speak(lastSpokenText, onStartCallback, onDoneCallback, onErrorCallback)
        }
    }

    override fun release() {
        stop()
        try {
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS", e)
        }
        textToSpeech = null
        isInitialized = false
        _state.value = TTSState.IDLE
        _isSpeaking.value = false
    }

    override fun applyProfile(profile: VoiceProfile): Boolean {
        currentProfile = profile
        val tts = textToSpeech ?: return false

        tts.setPitch(profile.pitch.coerceIn(0.5f, 2.0f))
        tts.setSpeechRate(profile.speechRate.coerceIn(0.5f, 2.0f))

        return configureHindiEngine(profile)
    }

    private fun configureHindiEngine(profile: VoiceProfile): Boolean {
        val tts = textToSpeech ?: return false
        val targetLocale = if (profile.language.startsWith("hi")) Locale("hi", "IN") else Locale.US

        val langResult = tts.setLanguage(targetLocale)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "Locale $targetLocale not supported, attempting fallback")
            val fallbackResult = tts.setLanguage(Locale.US)
            return fallbackResult != TextToSpeech.LANG_MISSING_DATA && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED
        }

        // Select the best available offline Hindi voice, preferring female if available
        selectBestVoice(tts, targetLocale)
        return true
    }

    private fun selectBestVoice(tts: TextToSpeech, targetLocale: Locale) {
        val voices = runCatching { tts.voices }.getOrNull() ?: emptySet()
        if (voices.isEmpty()) return

        _availableVoices.value = voices.map { it.name }

        val localeVoices = voices.filter { it.locale.language == targetLocale.language }
        if (localeVoices.isEmpty()) return

        // Priority order:
        // 1. Offline female voice
        // 2. Offline voice
        // 3. Any female voice
        // 4. Any voice in target locale
        val bestVoice = localeVoices
            .sortedWith(
                compareBy(
                    { it.isNetworkConnectionRequired }, // False (offline) first
                    { !isFemaleVoiceName(it.name) },    // Female first
                    { -it.quality }                      // Higher quality first
                )
            )
            .firstOrNull()

        if (bestVoice != null) {
            runCatching { tts.voice = bestVoice }
            Log.i(TAG, "Selected voice: ${bestVoice.name} (offline=${!bestVoice.isNetworkConnectionRequired})")
        }
    }

    private fun setupUtteranceListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
                _state.value = TTSState.SPEAKING
                onStartCallback?.invoke()
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.READY
                onDoneCallback?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                onErrorCallback?.invoke("TTS utterance error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                onErrorCallback?.invoke("TTS error code: $errorCode")
            }
        })
    }

    companion object {
        private const val TAG = "HindiSpeechService"
    }
}
