package com.vasu.assistant.core.tts

import android.content.Context
import android.util.Log
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TTSManager - Text-to-Speech manager implementing [HindiSpeechService].
 *
 * Architecture:
 * - Directs all speech requests through [VoiceRouter].
 * - Prioritizes Online Gemini Female TTS (Kore) when connected and configured.
 * - Prioritizes Offline Local TTS (custom assets and offline model) when offline.
 * - Disables silent Android system TTS fallback (only used as explicit emergency fallback).
 * - Full speech queue management with interruption protection.
 */
@Singleton
class TTSManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val speechQueue: SpeechQueue,
    private val settings: VasuSettings,
    private val customVoiceEngine: CustomVoiceEngine,
    private val voiceRouter: VoiceRouter,
    private val localTtsEngine: LocalTtsEngine
) : HindiSpeechService {

    private val scope = CoroutineScope(Dispatchers.Main)
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
    private val _availableLanguages = MutableStateFlow<List<Locale>>(listOf(Locale("hi", "IN"), Locale.US))
    val availableLanguages: StateFlow<List<Locale>> = _availableLanguages.asStateFlow()

    // Available voices list
    private val _availableVoices = MutableStateFlow<List<String>>(listOf("Vasu-Gemini-Female (Kore)", "Vasu-Local-Female"))
    override val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    // Voice Status (female indicator)
    private val _voiceStatus = MutableStateFlow(VoiceStatus(VoiceGender.FEMALE, "Kore (Gemini) / Local"))
    val voiceStatus: StateFlow<VoiceStatus> = _voiceStatus.asStateFlow()

    // Custom local voice model status
    val customVoiceStatus: StateFlow<VoiceModelStatus> = customVoiceEngine.status

    // Active voice source (Gemini, Local, Fallback)
    val activeVoiceSource: StateFlow<ActiveVoiceSource> = voiceRouter.currentSource

    private var onStartCallback: (() -> Unit)? = null
    private var onDoneCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    override fun initialize(profile: VoiceProfile, onReady: ((Boolean) -> Unit)?) {
        customVoiceEngine.detectCustomVoiceAssets()
        applyProfile(profile)
        isInitialized = true
        _state.value = TTSState.READY
        onReady?.invoke(true)
    }

    fun initialize() {
        initialize(settings.voiceProfile.value, null)
    }

    override fun isAvailable(): Boolean = isInitialized

    override fun isOfflineVoiceAvailable(): Boolean =
        customVoiceEngine.status.value == VoiceModelStatus.ACTIVE_CUSTOM_MODEL ||
        customVoiceEngine.status.value == VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES ||
        localTtsEngine.isAvailable

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

        if (!isInitialized) {
            initialize()
        }

        executeSpeak(text)
    }

    fun speak(text: String) {
        speak(text, null, null, null)
    }

    fun speakQueued(text: String) {
        if (!_isSpeaking.value) {
            speak(text)
        } else {
            speechQueue.enqueue(text)
        }
    }

    private fun executeSpeak(text: String) {
        val speakable = toSpeakableText(text)
        if (speakable.isBlank()) {
            onDoneCallback?.invoke()
            return
        }

        _isSpeaking.value = true
        _state.value = TTSState.SPEAKING
        _events.tryEmit(TTSEvent.SpeakingStarted(speakable))
        onStartCallback?.invoke()

        scope.launch {
            val handled = voiceRouter.speak(
                text = speakable,
                onStart = {
                    _isSpeaking.value = true
                    _state.value = TTSState.SPEAKING
                },
                onDone = {
                    _isSpeaking.value = false
                    _state.value = TTSState.READY
                    _events.tryEmit(TTSEvent.SpeakingCompleted(speakable))
                    onDoneCallback?.invoke()
                    processNextInQueue()
                },
                onError = { err ->
                    _isSpeaking.value = false
                    _state.value = TTSState.ERROR
                    _events.tryEmit(TTSEvent.Error(err))
                    onErrorCallback?.invoke(err)
                    processNextInQueue()
                }
            )

            if (!handled) {
                _isSpeaking.value = false
                _state.value = TTSState.ERROR
                val err = "Could not synthesize voice response"
                _events.tryEmit(TTSEvent.Error(err))
                onErrorCallback?.invoke(err)
                processNextInQueue()
            }
        }
    }

    override fun stop() {
        voiceRouter.stop()
        _isSpeaking.value = false
        isPaused = false
        speechQueue.clear()
        if (isInitialized) {
            _state.value = TTSState.READY
        }
    }

    override fun pause() {
        voiceRouter.stop()
        _isSpeaking.value = false
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
        isInitialized = false
        _state.value = TTSState.IDLE
    }

    override fun applyProfile(profile: VoiceProfile): Boolean {
        _currentProfile.value = profile
        return true
    }

    fun isSpeaking(): Boolean = _isSpeaking.value || customVoiceEngine.isPlaying()

    fun shutdown() {
        release()
    }

    private fun processNextInQueue() {
        val nextItem = speechQueue.dequeue()
        if (nextItem != null) {
            executeSpeak(nextItem.text)
        }
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
