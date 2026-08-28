package com.vasu.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.VoiceProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isToolExecution: Boolean = false,
    val toolName: String? = null,
    val toolResult: String? = null
)

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isListening: Boolean = false,
    val partialTranscript: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sttManager: STTManager,
    private val ttsManager: TTSManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Initialize TTS
        ttsManager.initialize(VoiceProfile.VASU_DEFAULT)

        // Welcome message
        addMessage(
            ChatMessage(
                content = "Hello! I am VASU, your voice assistant. How can I help you today?",
                isUser = false
            )
        )

        // Collect STT state
        viewModelScope.launch {
            sttManager.state.collect { sttState ->
                _uiState.value = _uiState.value.copy(
                    isListening = sttState == STTState.LISTENING
                )
            }
        }

        // Collect partial results
        viewModelScope.launch {
            sttManager.partialResults.collect { transcript ->
                _uiState.value = _uiState.value.copy(partialTranscript = transcript)
            }
        }

        // Collect final results
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(partialTranscript = "")
                    updateInput(result.text)
                    sendMessage()
                }
            }
        }

        // Collect STT errors
        viewModelScope.launch {
            sttManager.errors.collect { error ->
                addMessage(
                    ChatMessage(
                        content = "Voice error: $error",
                        isUser = false
                    )
                )
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        addMessage(ChatMessage(content = text, isUser = true))
        _uiState.value = _uiState.value.copy(inputText = "", isLoading = true)

        // Phase 6: AI Orchestrator will handle this
        viewModelScope.launch {
            kotlinx.coroutines.delay(1000)
            val response = "I received your message: \"$text\"\n\nAI engine will be connected in Phase 6. Stay tuned!"
            addMessage(
                ChatMessage(
                    content = response,
                    isUser = false
                )
            )
            _uiState.value = _uiState.value.copy(isLoading = false)

            // Speak response
            ttsManager.speakQueued(response)
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

    private fun addMessage(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
