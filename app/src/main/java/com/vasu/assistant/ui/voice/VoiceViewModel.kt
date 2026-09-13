package com.vasu.assistant.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.stt.SttErrorKind
import com.vasu.assistant.core.tts.ActiveVoiceSource
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import com.vasu.assistant.core.voice.GeminiVoiceState
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val rmsLevel: Float = 0f
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val sttManager: STTManager,
    private val ttsManager: TTSManager,
    private val aiOrchestrator: AIOrchestrator,
    private val settings: VasuSettings,
    private val geminiLiveVoiceService: GeminiLiveVoiceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        ttsManager.initialize()

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

                if (liveState != GeminiVoiceState.IDLE) {
                    _uiState.value = _uiState.value.copy(
                        voiceState = liveState,
                        mode = liveMode,
                        statusMessage = liveMode.labelHindi,
                        isSpeaking = liveState == GeminiVoiceState.SPEAKING,
                        isThinking = liveState == GeminiVoiceState.THINKING,
                        isListening = liveState == GeminiVoiceState.LISTENING
                    )
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

        // STT Errors
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
                    isListening = false
                )
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

    companion object {
        private const val TAG = "VoiceViewModel"
        private const val DEDUP_WINDOW_MS = 3000L
        private const val MAX_PENDING_COMMANDS = 20
    }

    fun toggleListening() {
        if (_uiState.value.isListening) {
            geminiLiveVoiceService.stopMicrophoneConversation()
            sttManager.stopListening()
        } else {
            // Barge-in: the user interrupted to take a new turn. Cancel the
            // backlog so the NEW utterance becomes the live turn — otherwise a
            // fresh transcript would jump ahead of older queued commands and
            // break FIFO order. (Uninterrupted A -> B -> C -> D flow keeps
            // strict FIFO with no loss; only an explicit interrupt cancels.)
            pendingVoiceCommands.clear()
            voiceBusy = false
            ttsManager.stop()
            geminiLiveVoiceService.stopSpeaking()

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
    }

    /**
     * TEXT-ONLY TEST: Test Gemini Live session with text and receive native Kore audio.
     */
    fun testKoreVoice(text: String = "Namaste Vasu, ek chhota sa greeting bolo.") {
        geminiLiveVoiceService.sendTextTurn(text)
    }

    @Deprecated("Use testKoreVoice instead", ReplaceWith("testKoreVoice(text)"))
    fun testErinomeVoice(text: String = "Namaste Vasu, ek chhota sa greeting bolo.") {
        testKoreVoice(text)
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
        val next = pendingVoiceCommands.removeFirstOrNull() ?: return
        android.util.Log.i(TAG, "Dequeueing next voice command (${pendingVoiceCommands.size} remaining)")
        processVoiceCommand(next)
    }

    override fun onCleared() {
        super.onCleared()
        geminiLiveVoiceService.disconnect()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
