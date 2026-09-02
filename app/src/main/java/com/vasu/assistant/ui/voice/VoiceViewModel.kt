package com.vasu.assistant.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.TTSState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val transcript: String = "",
    val lastResponse: String = "",
    val sttState: STTState = STTState.IDLE,
    val ttsState: TTSState = TTSState.IDLE
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val sttManager: STTManager,
    private val ttsManager: TTSManager,
    private val aiOrchestrator: AIOrchestrator
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    init {
        // Initialize TTS
        ttsManager.initialize()

        // Collect STT state
        viewModelScope.launch {
            sttManager.state.collect { sttState ->
                _uiState.value = _uiState.value.copy(
                    isListening = sttState == STTState.LISTENING,
                    sttState = sttState
                )
            }
        }

        // Collect TTS state
        viewModelScope.launch {
            ttsManager.state.collect { ttsState ->
                _uiState.value = _uiState.value.copy(
                    isSpeaking = ttsState == TTSState.SPEAKING,
                    ttsState = ttsState
                )
            }
        }

        // Collect partial results
        viewModelScope.launch {
            sttManager.partialResults.collect { transcript ->
                _uiState.value = _uiState.value.copy(transcript = transcript)
            }
        }

        // Collect final results
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(transcript = "")
                    processVoiceCommand(result.text)
                }
            }
        }

        // Collect STT errors
        viewModelScope.launch {
            sttManager.errors.collect { error ->
                _uiState.value = _uiState.value.copy(
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
            _uiState.value = _uiState.value.copy(lastResponse = "सोच रही हूँ...")

            val response = aiOrchestrator.processInput(trimmed)
            _uiState.value = _uiState.value.copy(lastResponse = response)

            // Speak response
            ttsManager.speakQueued(response)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
