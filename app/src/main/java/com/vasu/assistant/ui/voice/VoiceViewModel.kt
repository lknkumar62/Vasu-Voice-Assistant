package com.vasu.assistant.ui.voice

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.stt.SttErrorKind
import com.vasu.assistant.core.tts.ActiveVoiceSource
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import com.vasu.assistant.core.voice.GeminiVoiceState
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceUiMode(val labelHindi: String, val labelEnglish: String) {
    IDLE("बोलने के लिए माइक दबाएं", "Tap mic to speak"),
    CONNECTING("जेमिनी लाइव से कनेक्ट हो रहा है...", "Connecting to Gemini Live..."),
    CONNECTED("लाइव वॉइस तैयार है (Kore)", "Live voice ready (Kore)"),
    LISTENING("आपकी आवाज़ सुन रही हूँ...", "Listening..."),
    PROCESSING("कमांड प्रोसेस कर रही हूँ...", "Processing..."),
    THINKING("सोच रही हूँ...", "Thinking..."),
    SPEAKING("बोल रही हूँ...", "Speaking..."),
    DISCONNECTED("सत्र समाप्त हो गया", "Disconnected"),
    ERROR("त्रुटि हुई", "Error"),
    OFFLINE_MODE("ऑफ़लाइन मोड (लोकल वॉइस)", "Offline mode"),
    GEMINI_UNAVAILABLE("ऑनलाइन एआई अनुपलब्ध है (ऑफ़लाइन मोड)", "Gemini unavailable"),
    MIC_UNAVAILABLE("माइक्रोफ़ोन उपलब्ध नहीं है", "Microphone unavailable"),
    PERMISSION_REQUIRED("माइक्रोफ़ोन अनुमति आवश्यक है", "Permission required")
}

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val mode: VoiceUiMode = VoiceUiMode.IDLE,
    val voiceState: GeminiVoiceState = GeminiVoiceState.IDLE,
    val statusMessage: String = "बोलने के लिए माइक दबाएं",
    val activeVoiceSource: ActiveVoiceSource = ActiveVoiceSource.LOCAL_OFFLINE,
    val transcript: String = "",
    val lastResponse: String = "",
    val sttState: STTState = STTState.IDLE,
    val ttsState: TTSState = TTSState.IDLE,
    val rmsLevel: Float = 0f,
    val errorMessage: String? = null,
    val isWakeWordActive: Boolean = false,
    val wakeWordState: WakeWordState = WakeWordState.IDLE,
    val wakeWordReason: String? = null,
    val geminiModel: String = VasuSettings.DEFAULT_GEMINI_TTS_MODEL, // display model badge
    val geminiVoice: String = VasuSettings.DEFAULT_GEMINI_TTS_VOICE // display voice badge
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sttManager: STTManager,
    private val ttsManager: TTSManager,
    private val aiOrchestrator: AIOrchestrator,
    private val settings: VasuSettings,
    private val geminiLiveVoiceService: GeminiLiveVoiceService,
    private val wakeWordDetector: WakeWordDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        ttsManager.initialize()

        // Settings collectors for parity badges & diagnostics
        viewModelScope.launch {
            settings.geminiTtsModel.collect { model ->
                _uiState.value = _uiState.value.copy(geminiModel = model.ifBlank { "gemini-3.1-flash-live-preview" })
            }
        }
        viewModelScope.launch {
            settings.geminiTtsVoice.collect { voice ->
                _uiState.value = _uiState.value.copy(geminiVoice = voice.ifBlank { "Kore" })
            }
        }
        viewModelScope.launch {
            settings.wakeWordEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isWakeWordActive = enabled)
            }
        }
        viewModelScope.launch {
            wakeWordDetector.state.collect { wwState ->
                _uiState.value = _uiState.value.copy(wakeWordState = wwState)
            }
        }
        viewModelScope.launch {
            wakeWordDetector.unavailableReason.collect { reason ->
                _uiState.value = _uiState.value.copy(wakeWordReason = reason)
            }
        }

        // Gemini Live Voice State
        viewModelScope.launch {
            geminiLiveVoiceService.voiceState.collect { liveState ->
                val liveMode = when (liveState) {
                    GeminiVoiceState.IDLE -> VoiceUiMode.IDLE
                    GeminiVoiceState.CONNECTING -> VoiceUiMode.CONNECTING
                    GeminiVoiceState.CONNECTED -> VoiceUiMode.CONNECTED
                    GeminiVoiceState.LISTENING -> VoiceUiMode.LISTENING
                    GeminiVoiceState.THINKING -> VoiceUiMode.THINKING
                    GeminiVoiceState.SPEAKING -> VoiceUiMode.SPEAKING
                    GeminiVoiceState.DISCONNECTED -> VoiceUiMode.DISCONNECTED
                    GeminiVoiceState.ERROR -> VoiceUiMode.ERROR
                }

                if (liveState == GeminiVoiceState.ERROR) {
                    val msg = _uiState.value.errorMessage ?: "Gemini Live connection error. Check API key & internet, then Reconnect."
                    _uiState.value = _uiState.value.copy(
                        voiceState = liveState,
                        mode = liveMode,
                        statusMessage = liveMode.labelHindi,
                        isSpeaking = false,
                        isThinking = false,
                        isListening = false,
                        errorMessage = msg
                    )
                } else if (liveState != GeminiVoiceState.IDLE) {
                    _uiState.value = _uiState.value.copy(
                        voiceState = liveState,
                        mode = liveMode,
                        statusMessage = liveMode.labelHindi,
                        isSpeaking = liveState == GeminiVoiceState.SPEAKING,
                        isThinking = liveState == GeminiVoiceState.THINKING,
                        isListening = liveState == GeminiVoiceState.LISTENING,
                        errorMessage = if (liveState == GeminiVoiceState.CONNECTED || liveState == GeminiVoiceState.CONNECTING) null else _uiState.value.errorMessage
                    )
                    // Clear error when connected
                    if (liveState == GeminiVoiceState.CONNECTED) {
                        _uiState.value = _uiState.value.copy(errorMessage = null)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        voiceState = liveState
                    )
                }
            }
        }

        // Live responses
        viewModelScope.launch {
            geminiLiveVoiceService.lastResponse.collect { resp ->
                if (resp.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(lastResponse = resp)
                }
            }
        }

        // STT State
        viewModelScope.launch {
            sttManager.state.collect { sttState ->
                val listening = sttState == STTState.LISTENING
                val processing = sttState == STTState.PROCESSING
                val newMode = when {
                    listening -> VoiceUiMode.LISTENING
                    processing -> VoiceUiMode.PROCESSING
                    _uiState.value.isThinking -> VoiceUiMode.THINKING
                    _uiState.value.isSpeaking -> VoiceUiMode.SPEAKING
                    settings.offlineOnly.value -> VoiceUiMode.OFFLINE_MODE
                    else -> VoiceUiMode.IDLE
                }

                _uiState.value = _uiState.value.copy(
                    isListening = listening,
                    sttState = sttState,
                    mode = newMode,
                    statusMessage = newMode.labelHindi
                )
            }
        }

        // STT RMS
        viewModelScope.launch {
            sttManager.rmsLevel.collect { rms ->
                _uiState.value = _uiState.value.copy(rmsLevel = rms)
            }
        }

        // TTS State
        viewModelScope.launch {
            ttsManager.state.collect { ttsState ->
                val speaking = ttsState == TTSState.SPEAKING
                val newMode = when {
                    speaking -> VoiceUiMode.SPEAKING
                    _uiState.value.isListening -> VoiceUiMode.LISTENING
                    _uiState.value.isThinking -> VoiceUiMode.THINKING
                    settings.offlineOnly.value -> VoiceUiMode.OFFLINE_MODE
                    else -> VoiceUiMode.IDLE
                }

                _uiState.value = _uiState.value.copy(
                    isSpeaking = speaking,
                    ttsState = ttsState,
                    mode = newMode,
                    statusMessage = newMode.labelHindi
                )
            }
        }

        // Active voice source
        viewModelScope.launch {
            ttsManager.activeVoiceSource.collect { source ->
                _uiState.value = _uiState.value.copy(activeVoiceSource = source)
            }
        }

        // Partial results
        viewModelScope.launch {
            sttManager.partialResults.collect { transcript ->
                _uiState.value = _uiState.value.copy(
                    transcript = transcript,
                    mode = VoiceUiMode.LISTENING,
                    statusMessage = VoiceUiMode.LISTENING.labelHindi
                )
            }
        }

        // Final results — final transcripts only, never partials.
        // While a response is speaking, the transcript is queued FIFO and
        // runs only after the current TTS completes (no overlap, no loss).
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(transcript = "")
                    onVoiceTranscript(result.text)
                }
            }
        }

        // STT Errors — auto-restart if continuous mode (except permission)
        viewModelScope.launch {
            sttManager.errors.collect { error ->
                val errorMode = when (error.kind) {
                    SttErrorKind.MIC_PERMISSION_DENIED -> VoiceUiMode.PERMISSION_REQUIRED
                    SttErrorKind.MIC_BUSY, SttErrorKind.AUDIO_ERROR -> VoiceUiMode.MIC_UNAVAILABLE
                    SttErrorKind.NETWORK_ERROR -> VoiceUiMode.OFFLINE_MODE
                    SttErrorKind.SERVICE_UNAVAILABLE -> VoiceUiMode.MIC_UNAVAILABLE
                    else -> VoiceUiMode.IDLE
                }

                _uiState.value = _uiState.value.copy(
                    mode = errorMode,
                    statusMessage = error.message,
                    lastResponse = error.message,
                    errorMessage = error.message,
                    isListening = false
                )
                // For transient errors in continuous mode, retry listening after brief delay
                if (continuousListening && error.kind != SttErrorKind.MIC_PERMISSION_DENIED) {
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(800)
                        if (continuousListening && !voiceBusy && !_uiState.value.isSpeaking) {
                            android.util.Log.i(TAG, "Retrying listening after error: ${error.kind}")
                            val liveStarted = geminiLiveVoiceService.startMicrophoneConversation()
                            if (!liveStarted) sttManager.startListening()
                        }
                    }
                }
            }
        }
    }

    /** Tracks last processed command to suppress STT double-emits (short window only). */
    private var lastProcessedCommand: String? = null
    private var lastProcessedAtMs: Long = 0L

    /** Tracks last spoken response ID to prevent duplicate TTS. */
    private var lastSpokenResponseId: String? = null

    /**
     * FIFO of voice commands captured while VASU is busy (thinking/speaking).
     * Commands are NEVER executed mid-playback: each waits for the current
     * response's TTS_COMPLETED, then runs through the normal pipeline.
     */
    private val pendingVoiceCommands = ArrayDeque<String>()
    private var voiceBusy = false

    /** When true, mic stays hot — single tap keeps listening in background. */
    private var continuousListening = false

    companion object {
        private const val TAG = "VoiceViewModel"
        private const val DEDUP_WINDOW_MS = 3000L
        private const val MAX_PENDING_COMMANDS = 20
        // Web parity diagnostics constants (same as VoiceView.tsx)
        const val DIAGNOSTICS_INPUT = "16,000 Hz Mono PCM"
        const val DIAGNOSTICS_OUTPUT = "24,000 Hz Little-Endian PCM"
        const val DISPLAY_MODEL = "gemini-3.1-flash-live-preview"
    }

    fun toggleListening() {
        if (_uiState.value.isListening || continuousListening) {
            // User wants to STOP continuous listening
            continuousListening = false
            geminiLiveVoiceService.stopMicrophoneConversation()
            sttManager.stopListening()
            _uiState.value = _uiState.value.copy(
                isListening = false,
                mode = VoiceUiMode.IDLE,
                statusMessage = VoiceUiMode.IDLE.labelHindi
            )
            android.util.Log.i(TAG, "Continuous listening STOPPED by user")
        } else {
            // Start continuous listening — single tap, stays hot in background
            continuousListening = true
            pendingVoiceCommands.clear()
            voiceBusy = false
            ttsManager.stop()
            geminiLiveVoiceService.stopSpeaking()
            // Clear previous error when user retries
            _uiState.value = _uiState.value.copy(errorMessage = null)

            val liveStarted = geminiLiveVoiceService.startMicrophoneConversation()
            if (!liveStarted) {
                sttManager.startListening()
            }
            android.util.Log.i(TAG, "Continuous listening STARTED — single tap, stays in background")
        }
    }

    /** Restart listening if continuous mode is active and mic is free */
    private fun restartListeningIfNeeded() {
        if (!continuousListening) return
        if (voiceBusy || _uiState.value.isSpeaking || _uiState.value.isThinking) return
        if (pendingVoiceCommands.isNotEmpty()) return // drain will handle next
        viewModelScope.launch {
            kotlinx.coroutines.delay(300) // small gap to avoid self-trigger
            if (!continuousListening) return@launch
            if (voiceBusy || _uiState.value.isSpeaking || _uiState.value.isThinking) return@launch
            android.util.Log.i(TAG, "Auto-restarting listening (continuous mode)")
            val liveStarted = geminiLiveVoiceService.startMicrophoneConversation()
            if (!liveStarted) {
                sttManager.startListening()
            }
        }
    }

    fun stopSpeaking() {
        // Explicit user stop: silence everything, drop queued turns, free mic.
        pendingVoiceCommands.clear()
        voiceBusy = false
        geminiLiveVoiceService.stopSpeaking()
        ttsManager.stop()
        // If in continuous mode, restart listening after stop
        restartListeningIfNeeded()
    }

    /**
     * TEXT-ONLY TEST: Test Gemini Live session with text and receive native Kore audio.
     */
    fun testKoreVoice(text: String = "Namaste Vasu, ek chhota sa greeting bolo.") {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        geminiLiveVoiceService.sendTextTurn(text)
    }

    @Deprecated("Use testKoreVoice instead", ReplaceWith("testKoreVoice(text)"))
    fun testErinomeVoice(text: String = "Namaste Vasu, ek chhota sa greeting bolo.") {
        testKoreVoice(text)
    }

    // ── Maya parity additions ────────────────────────────────────────

    fun toggleWakeWord() {
        val enabled = !settings.wakeWordEnabled.value
        settings.setWakeWordEnabled(enabled)
        if (enabled) {
            wakeWordDetector.initialize()
            VasuForegroundService.start(appContext)
            android.util.Log.i(TAG, "Wake word ENABLED from VoiceScreen")
        } else {
            VasuForegroundService.stop(appContext)
            wakeWordDetector.stop()
            android.util.Log.i(TAG, "Wake word DISABLED from VoiceScreen")
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                errorMessage = null,
                mode = VoiceUiMode.CONNECTING,
                statusMessage = VoiceUiMode.CONNECTING.labelHindi
            )
            val ok = geminiLiveVoiceService.ensureConnectedSuspend()
            if (!ok) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Connection failed. Verify Gemini API Key in Settings and internet.",
                    mode = VoiceUiMode.ERROR,
                    statusMessage = VoiceUiMode.ERROR.labelHindi
                )
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = null)
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
        if (_uiState.value.mode == VoiceUiMode.ERROR || _uiState.value.mode == VoiceUiMode.PERMISSION_REQUIRED) {
            _uiState.value = _uiState.value.copy(mode = VoiceUiMode.IDLE, statusMessage = VoiceUiMode.IDLE.labelHindi)
        }
    }

    fun testSpeakerHardware() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            try {
                // Use TTSManager queue to verify audio pipeline plays through speaker
                ttsManager.speakQueued("नमस्ते! मैं कोर हूँ। वासु वॉइस असिस्टेंट बिल्कुल ठीक काम कर रहा है। Speaker hardware test successful.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = "Hardware speaker test failed: ${e.message}")
            }
        }
    }

    private fun onVoiceTranscript(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        // De-duplication: skip STT double-emits of the same phrase within a
        // short window, but allow genuine repeats in long conversations.
        val now = System.currentTimeMillis()
        if (trimmed == lastProcessedCommand && now - lastProcessedAtMs < DEDUP_WINDOW_MS) {
            return
        }

        if (voiceBusy || _uiState.value.isSpeaking || _uiState.value.isThinking) {
            if (pendingVoiceCommands.size >= MAX_PENDING_COMMANDS) {
                pendingVoiceCommands.removeFirst()
            }
            pendingVoiceCommands.addLast(trimmed)
            lastProcessedCommand = trimmed
            lastProcessedAtMs = now
            android.util.Log.i(TAG, "Queued voice command during playback (${pendingVoiceCommands.size} pending)")
            _uiState.value = _uiState.value.copy(
                statusMessage = "Sun liya — bolne ke baad karungi (${pendingVoiceCommands.size})"
            )
            return
        }

        processVoiceCommand(trimmed)
    }

    private fun processVoiceCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) {
            drainPendingCommands()
            return
        }

        lastProcessedCommand = trimmed
        lastProcessedAtMs = System.currentTimeMillis()
        voiceBusy = true

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isThinking = true,
                mode = VoiceUiMode.THINKING,
                statusMessage = VoiceUiMode.THINKING.labelHindi,
                lastResponse = "सोच रही हूँ..."
            )

            val response = aiOrchestrator.processInput(trimmed)

            _uiState.value = _uiState.value.copy(
                isThinking = false,
                lastResponse = response
            )

            // Speak response — only once per response. The queued next
            // command starts only after this TTS actually completes.
            val responseId = "${trimmed.hashCode()}_${response.hashCode()}"
            if (lastSpokenResponseId != responseId) {
                lastSpokenResponseId = responseId
                ttsManager.speakQueued(response) {
                    voiceBusy = false
                    drainPendingCommands()
                }
            } else {
                voiceBusy = false
                drainPendingCommands()
            }
        }
    }

    /** FIFO: run the next queued command only after the current turn fully finished. */
    private fun drainPendingCommands() {
        val next = pendingVoiceCommands.removeFirstOrNull()
        if (next != null) {
            android.util.Log.i(TAG, "Dequeueing next voice command (${pendingVoiceCommands.size} remaining)")
            processVoiceCommand(next)
        } else {
            // Queue empty — if continuous mode, restart listening for next user utterance
            restartListeningIfNeeded()
        }
    }

    override fun onCleared() {
        super.onCleared()
        geminiLiveVoiceService.disconnect()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
