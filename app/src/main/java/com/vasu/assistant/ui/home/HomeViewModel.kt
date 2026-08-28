package com.vasu.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.TTSState
import com.vasu.assistant.core.tts.VoiceProfile
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val isWakeWordActive: Boolean = false,
    val lastMessage: String = "Hello! I am VASU. How can I help you?",
    val currentTranscript: String = "",
    val sttState: STTState = STTState.IDLE,
    val ttsState: TTSState = TTSState.IDLE,
    val wakeWordState: WakeWordState = WakeWordState.IDLE,
    val quickActions: List<QuickAction> = listOf(
        QuickAction("💬", "Chat", "chat"),
        QuickAction("🎤", "Voice", "voice"),
        QuickAction("🛡️", "Guardian", "guardian"),
        QuickAction("⚙️", "Settings", "settings")
    )
)

data class QuickAction(
    val icon: String,
    val label: String,
    val action: String
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sttManager: STTManager,
    private val ttsManager: TTSManager,
    private val wakeWordDetector: WakeWordDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // Initialize TTS
        ttsManager.initialize(VoiceProfile.VASU_DEFAULT)

        // Initialize wake word detector
        wakeWordDetector.initialize()

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

        // Collect wake word state
        viewModelScope.launch {
            wakeWordDetector.state.collect { wakeWordState ->
                _uiState.value = _uiState.value.copy(
                    wakeWordState = wakeWordState,
                    isWakeWordActive = wakeWordState == WakeWordState.LISTENING ||
                            wakeWordState == WakeWordState.DETECTED
                )
            }
        }

        // Collect partial STT results
        viewModelScope.launch {
            sttManager.partialResults.collect { transcript ->
                _uiState.value = _uiState.value.copy(currentTranscript = transcript)
            }
        }

        // Collect final STT results
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(currentTranscript = "")
                    processVoiceCommand(result.text)
                }
            }
        }

        // Collect STT errors
        viewModelScope.launch {
            sttManager.errors.collect { error ->
                _uiState.value = _uiState.value.copy(
                    lastMessage = "Error: $error",
                    isListening = false
                )
            }
        }

        // Collect wake word detections
        viewModelScope.launch {
            wakeWordDetector.detections.collect { wakePhrase ->
                // Wake word detected! Start listening for command
                sttManager.startListening()
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

    fun toggleWakeWord() {
        if (_uiState.value.isWakeWordActive) {
            wakeWordDetector.stop()
        } else {
            wakeWordDetector.start()
        }
    }

    fun setThinking(thinking: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isThinking = thinking,
                isListening = false
            )
        }
    }

    fun setSpeaking(speaking: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSpeaking = speaking,
                isThinking = false,
                isListening = false
            )
        }
    }

    fun updateMessage(message: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastMessage = message)
        }
    }

    fun speakResponse(text: String) {
        ttsManager.speakQueued(text)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    /**
     * Process voice command - Phase 6 will handle AI orchestration
     */
    private fun processVoiceCommand(command: String) {
        viewModelScope.launch {
            setThinking(true)
            updateMessage("Processing: \"$command\"")

            // Simulate processing (Phase 6: AI Orchestrator will replace this)
            kotlinx.coroutines.delay(1500)

            val response = "I heard: \"$command\"\n\nAI engine will be connected in Phase 6. Stay tuned!"
            updateMessage(response)
            setThinking(false)

            // Speak the response
            speakResponse(response)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.stopListening()
        ttsManager.stop()
        wakeWordDetector.destroy()
    }
}
