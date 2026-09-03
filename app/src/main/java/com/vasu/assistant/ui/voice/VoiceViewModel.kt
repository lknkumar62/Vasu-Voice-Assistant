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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VoiceUiMode(val labelHindi: String, val labelEnglish: String) {
    IDLE("बोलने के लिए माइक दबाएं", "Tap mic to speak"),
    LISTENING("आपकी आवाज़ सुन रही हूँ...", "Listening..."),
    PROCESSING("कमांड प्रोसेस कर रही हूँ...", "Processing..."),
    THINKING("सोच रही हूँ...", "Thinking..."),
    SPEAKING("बोल रही हूँ...", "Speaking..."),
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
    private val settings: VasuSettings
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        ttsManager.initialize()

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

        // Final results
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(transcript = "")
                    processVoiceCommand(result.text)
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

    fun toggleListening() {
        if (_uiState.value.isListening) {
            sttManager.stopListening()
        } else {
            sttManager.startListening()
        }
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    private fun processVoiceCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

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

            // Speak response via VoiceRouter
            ttsManager.speakQueued(response)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
