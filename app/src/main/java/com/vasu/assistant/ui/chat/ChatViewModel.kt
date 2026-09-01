package com.vasu.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
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
    private val ttsManager: TTSManager,
    private val aiOrchestrator: AIOrchestrator,
    private val conversationDao: com.vasu.assistant.database.ConversationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentConversationId = System.currentTimeMillis().toString()

    init {
        ttsManager.initialize()

        // Load chat history
        loadConversationHistory()

        viewModelScope.launch {
            sttManager.state.collect { sttState ->
                _uiState.value = _uiState.value.copy(
                    isListening = sttState == STTState.LISTENING
                )
            }
        }

        viewModelScope.launch {
            sttManager.partialResults.collect { transcript ->
                _uiState.value = _uiState.value.copy(partialTranscript = transcript)
            }
        }

        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal) {
                    _uiState.value = _uiState.value.copy(partialTranscript = "")
                    updateInput(result.text)
                    sendMessage()
                }
            }
        }

        viewModelScope.launch {
            sttManager.errors.collect { error ->
                addMessage(ChatMessage(content = error.message, isUser = false))
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

        viewModelScope.launch {
            val response = aiOrchestrator.processInput(text)
            addMessage(ChatMessage(content = response, isUser = false))
            _uiState.value = _uiState.value.copy(isLoading = false)
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
        // Save to database
        viewModelScope.launch {
            conversationDao.insertMessage(
                com.vasu.assistant.database.ConversationMessageEntity(
                    conversationId = currentConversationId,
                    role = if (message.isUser) "user" else "assistant",
                    content = message.content,
                    toolName = message.toolName,
                    toolResult = message.toolResult,
                    timestamp = message.timestamp
                )
            )
        }
    }

    private fun loadConversationHistory() {
        viewModelScope.launch {
            val messages = conversationDao.getGlobalRecentMessages(limit = 50).reversed()
            if (messages.isNotEmpty()) {
                val chatMessages = messages.map { entity ->
                    ChatMessage(
                        id = entity.id.toString(),
                        content = entity.content,
                        isUser = entity.role == "user",
                        timestamp = entity.timestamp,
                        toolName = entity.toolName,
                        toolResult = entity.toolResult
                    )
                }
                _uiState.value = _uiState.value.copy(messages = chatMessages)
            } else {
                _uiState.value = _uiState.value.copy(
                    messages = listOf(
                        ChatMessage(
                            content = "Hello! I am VASU, your voice assistant. How can I help you today?",
                            isUser = false
                        )
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        sttManager.stopListening()
        ttsManager.stop()
    }
}
