package com.vasu.assistant.core.voice

import android.content.Context
import android.util.Log
import com.vasu.assistant.core.ai.PromptManager
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GeminiLiveVoiceService - Main coordinator for real-time natural conversational voice
 * using Gemini Live native AUDIO with target voice "Kore".
 *
 * Implements:
 * USER MICROPHONE -> 16-bit PCM / 16 kHz / mono
 *       ↓
 * Gemini Live realtime session (WebSocket)
 *       ↓
 * native AUDIO response -> 16-bit PCM / 24 kHz
 *       ↓
 * audio playback -> VASU FEMALE VOICE (Kore)
 */
@Singleton
class GeminiLiveVoiceService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: SecureKeyStore,
    private val settings: VasuSettings,
    private val promptManager: PromptManager,
    private val liveSession: GeminiLiveSession,
    private val audioPlayer: NativeAudioPlayer,
    private val micRecorder: NativeMicrophoneRecorder
) {
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private val _voiceState = MutableStateFlow(GeminiVoiceState.IDLE)
    val voiceState: StateFlow<GeminiVoiceState> = _voiceState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _currentTranscript = MutableStateFlow("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse: StateFlow<String> = _lastResponse.asStateFlow()

    private var pendingOnDoneCallback: (() -> Unit)? = null

    companion object {
        private const val TAG = "GeminiLiveVoiceService"
        const val TARGET_VOICE = "Kore"
    }

    init {
        setupSessionCallbacks()
        observePlayerState()
    }

    private fun setupSessionCallbacks() {
        liveSession.setCallbacks(
            onAudioReceived = { audioBytes ->
                handleIncomingAudioChunk(audioBytes)
            },
            onTextReceived = { textPart ->
                _lastResponse.value = (_lastResponse.value + " " + textPart).trim()
            },
            onInterrupted = {
                handleInterruption()
            },
            onTurnComplete = {
                val doneCallback = pendingOnDoneCallback
                pendingOnDoneCallback = null
                audioPlayer.markEndOfResponse {
                    doneCallback?.invoke()
                }
            },
            onError = { errorReason ->
                _lastError.value = errorReason
                _voiceState.value = GeminiVoiceState.ERROR
                Log.e(TAG, "[GEMINI_KORE_AUDIO] Gemini Live session error: $errorReason")
            }
        )
    }

    private fun observePlayerState() {
        serviceScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                if (playing) {
                    _voiceState.value = GeminiVoiceState.SPEAKING
                } else if (_voiceState.value == GeminiVoiceState.SPEAKING) {
                    _voiceState.value = if (micRecorder.isRecording.value) {
                        GeminiVoiceState.LISTENING
                    } else {
                        GeminiVoiceState.CONNECTED
                    }
                }
            }
        }
    }

    /**
     * Build the standard VASU concise system prompt.
     */
    private fun getSystemInstruction(): String {
        return promptManager.buildPrompt(
            includeTools = false,
            includeGuardian = false,
            includeMemory = false
        ).toString()
    }

    /**
     * Coroutine-safe connection check that waits for actual READY state.
     */
    suspend fun ensureConnectedSuspend(): Boolean {
        if (liveSession.isReady()) {
            return true
        }

        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            val err = "Gemini API key is not configured"
            _lastError.value = err
            _voiceState.value = GeminiVoiceState.ERROR
            Log.w(TAG, "[GEMINI_KORE_AUDIO] Cannot start voice session: $err")
            return false
        }

        _voiceState.value = GeminiVoiceState.CONNECTING
        val ready = liveSession.connectAndWaitReady(apiKey, getSystemInstruction())
        if (ready) {
            _voiceState.value = GeminiVoiceState.CONNECTED
            _lastError.value = null
        } else {
            val err = liveSession.lastErrorMessage ?: "Gemini Live session connection failed"
            _lastError.value = err
            _voiceState.value = GeminiVoiceState.ERROR
            Log.e(TAG, "[GEMINI_KORE_AUDIO] Session connection failed: $err")
        }
        return ready
    }

    /**
     * Connect to the Gemini Live session asynchronously.
     */
    fun connectSession(): Boolean {
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            _voiceState.value = GeminiVoiceState.ERROR
            _lastError.value = "Gemini API key missing"
            Log.w(TAG, "[GEMINI_KORE_AUDIO] Cannot start voice session: Gemini API key missing")
            return false
        }

        _voiceState.value = GeminiVoiceState.CONNECTING
        serviceScope.launch {
            ensureConnectedSuspend()
        }
        return true
    }

    /**
     * Primary speech synthesis function:
     * Sends a text prompt to Gemini Live ("Kore"), receives 24 kHz PCM audio, decodes and
     * streams directly through NativeAudioPlayer -> AudioTrack -> Speaker.
     * Suspends until the hardware audio playback has completely finished playing out.
     */
    suspend fun speakText(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            val err = "Gemini API key is not configured in settings"
            _lastError.value = err
            _voiceState.value = GeminiVoiceState.ERROR
            Log.w(TAG, "[GEMINI_KORE_AUDIO] Cannot speak text turn: $err")
            onError?.invoke(err)
            return false
        }

        val ready = ensureConnectedSuspend()
        if (!ready) {
            val err = liveSession.lastErrorMessage ?: _lastError.value ?: "Gemini Live session failed to reach READY state"
            _lastError.value = err
            _voiceState.value = GeminiVoiceState.ERROR
            Log.e(TAG, "[GEMINI_KORE_AUDIO] Cannot speak text turn: $err")
            onError?.invoke(err)
            return false
        }

        val turnCompletion = CompletableDeferred<Boolean>()
        pendingOnDoneCallback = {
            onDone?.invoke()
            turnCompletion.complete(true)
        }

        _voiceState.value = GeminiVoiceState.THINKING
        _currentTranscript.value = text
        _lastResponse.value = ""
        onStart?.invoke()

        val sent = liveSession.sendTextTurn(text)
        if (!sent) {
            val err = liveSession.lastErrorMessage ?: "Failed to send text turn to Gemini Live WebSocket"
            _lastError.value = err
            _voiceState.value = GeminiVoiceState.ERROR
            Log.e(TAG, "[GEMINI_KORE_AUDIO] $err")
            onError?.invoke(err)
            return false
        }

        return try {
            turnCompletion.await()
        } catch (e: Exception) {
            val err = "Turn completion error: ${e.message}"
            _lastError.value = err
            onError?.invoke(err)
            false
        }
    }

    /**
     * Non-suspending turn invocation wrapper.
     */
    fun sendTextTurn(
        promptText: String = "Namaste Vasu, ek chhota sa greeting bolo.",
        onDone: (() -> Unit)? = null
    ): Boolean {
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            _voiceState.value = GeminiVoiceState.ERROR
            _lastError.value = "Gemini API key missing"
            Log.w(TAG, "[GEMINI_KORE_AUDIO] Cannot start voice session: Gemini API key missing")
            return false
        }

        serviceScope.launch {
            speakText(promptText, onDone = onDone)
        }
        return true
    }

    /**
     * Starts continuous microphone streaming for natural real-time voice conversation.
     * Safely waits for the READY state before allowing microphone chunks to stream.
     */
    fun startMicrophoneConversation(): Boolean {
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            _voiceState.value = GeminiVoiceState.ERROR
            Log.w(TAG, "Cannot start voice session: Gemini API key missing")
            return false
        }

        _voiceState.value = GeminiVoiceState.CONNECTING

        serviceScope.launch {
            val isReady = ensureConnectedSuspend()
            if (!isReady) {
                _voiceState.value = GeminiVoiceState.ERROR
                Log.e(TAG, "Cannot start microphone conversation: Gemini Live session not ready")
                return@launch
            }

            _voiceState.value = GeminiVoiceState.LISTENING

            val started = micRecorder.startStreaming { pcmChunk ->
                // Interruption detection: if user speaks while assistant is speaking, halt assistant immediately
                if (audioPlayer.isPlaying.value) {
                    handleInterruption()
                }
                if (liveSession.isReady()) {
                    liveSession.sendAudioChunk(pcmChunk)
                }
            }

            if (!started) {
                _voiceState.value = GeminiVoiceState.ERROR
            }
        }

        return true
    }

    /**
     * Stop microphone streaming.
     */
    fun stopMicrophoneConversation() {
        micRecorder.stopStreaming()
        if (_voiceState.value == GeminiVoiceState.LISTENING) {
            _voiceState.value = GeminiVoiceState.CONNECTED
        }
    }

    /**
     * Stop assistant speech immediately and clear audio buffer.
     */
    fun stopSpeaking() {
        audioPlayer.stopAndFlush()
        if (_voiceState.value == GeminiVoiceState.SPEAKING) {
            _voiceState.value = if (micRecorder.isRecording.value) {
                GeminiVoiceState.LISTENING
            } else {
                GeminiVoiceState.CONNECTED
            }
        }
    }

    /**
     * Handle interruption (from client speech detector or server interrupted message).
     */
    private fun handleInterruption() {
        Log.d(TAG, "[VASU] Handling interruption - halting audio playback")
        audioPlayer.stopAndFlush()
        if (micRecorder.isRecording.value) {
            _voiceState.value = GeminiVoiceState.LISTENING
        }
    }

    private fun handleIncomingAudioChunk(audioBytes: ByteArray) {
        if (audioBytes.isNotEmpty()) {
            _voiceState.value = GeminiVoiceState.SPEAKING
            Log.d(TAG, "[GEMINI_KORE_AUDIO] Streaming ${audioBytes.size} PCM bytes to NativeAudioPlayer (AudioTrack)")
            audioPlayer.enqueueAudioChunk(audioBytes)
        }
    }

    fun isSessionReady(): Boolean = liveSession.isReady()

    /**
     * Disconnect session and release audio hardware.
     */
    fun disconnect() {
        stopMicrophoneConversation()
        audioPlayer.stopAndFlush()
        liveSession.disconnect()
        _voiceState.value = GeminiVoiceState.DISCONNECTED
    }
}
