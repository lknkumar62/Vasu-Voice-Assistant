package com.vasu.assistant.core.voice

import android.content.Context
import android.util.Log
import com.vasu.assistant.core.ai.PromptManager
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * audio playback -> VASU FEMALE VOICE
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
                _voiceState.value = GeminiVoiceState.ERROR
                Log.e(TAG, "Gemini Live session error: $errorReason")
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
            _voiceState.value = GeminiVoiceState.ERROR
            Log.w(TAG, "Cannot start voice session: Gemini API key missing")
            return false
        }

        _voiceState.value = GeminiVoiceState.CONNECTING
        val ready = liveSession.connectAndWaitReady(apiKey, getSystemInstruction())
        if (ready) {
            _voiceState.value = GeminiVoiceState.CONNECTED
        } else {
            _voiceState.value = GeminiVoiceState.ERROR
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
            Log.w(TAG, "Cannot start voice session: Gemini API key missing")
            return false
        }

        _voiceState.value = GeminiVoiceState.CONNECTING
        serviceScope.launch {
            ensureConnectedSuspend()
        }
        return true
    }

    /**
     * TEST 4 & TEXT-ONLY TEST:
     * Sends a text prompt to Gemini Live ("Namaste Vasu, ek chhota sa greeting bolo."),
     * waits for READY state safely, receives native 24 kHz AUDIO, decodes and plays immediately.
     */
    fun sendTextTurn(
        promptText: String = "Namaste Vasu, ek chhota sa greeting bolo.",
        onDone: (() -> Unit)? = null
    ): Boolean {
        val apiKey = keyStore.getGeminiKey()
        if (apiKey.isNullOrBlank()) {
            _voiceState.value = GeminiVoiceState.ERROR
            Log.w(TAG, "Cannot start voice session: Gemini API key missing")
            return false
        }

        pendingOnDoneCallback = onDone

        serviceScope.launch {
            val isReady = ensureConnectedSuspend()
            if (!isReady) {
                _voiceState.value = GeminiVoiceState.ERROR
                Log.e(TAG, "Cannot send text: Gemini Live session not ready")
                return@launch
            }

            _voiceState.value = GeminiVoiceState.THINKING
            _currentTranscript.value = promptText
            _lastResponse.value = ""

            val sent = liveSession.sendTextTurn(promptText)
            if (!sent) {
                _voiceState.value = GeminiVoiceState.ERROR
                Log.e(TAG, "Failed to send text turn")
            }
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
