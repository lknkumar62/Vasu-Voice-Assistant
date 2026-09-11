package com.vasu.assistant.ui.chat

import android.util.Log
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

/**
 * ChatViewModel - Manages chat message flow with strict event-based TTS control.
 *
 * RULES:
 * - TTS is triggered ONLY from sendMessage() for NEW user-submitted messages
 * - STT results auto-send is DISABLED (user must explicitly press Send or Mic)
 * - Each response has a unique ID; TTS is spoken exactly once per response
 * - No TTS on chat open, history load, scroll, recomposition, or tab switch
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sttManager: STTManager,
    private val ttsManager: TTSManager,
    private val aiOrchestrator: AIOrchestrator,
    private val conversationDao: com.vasu.assistant.database.ConversationDao
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val currentConversationId = System.currentTimeMillis().toString()

    private val sendMutex = kotlinx.coroutines.sync.Mutex()

    /** Tracks the last response ID that was sent to TTS to prevent duplicate speaking. */
    private var lastSpokenResponseId: String? = null

    init {
        ttsManager.initialize()

        // Load chat history — does NOT trigger TTS
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

        // STT final results — do NOT auto-send. User must explicitly press Send or Mic.
        // Only update the input field so user can review before sending.
        viewModelScope.launch {
            sttManager.results.collect { result ->
                if (result.isFinal && result.text.isNotBlank()) {
                    Log.d(TAG, "STT final result: ${result.text}")
                    _uiState.value = _uiState.value.copy(partialTranscript = "")
                    // Just fill the input field — do NOT auto-send
                    updateInput(result.text)
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
        if (text.isEmpty() || _uiState.value.isLoading) return

        viewModelScope.launch {
            if (!sendMutex.tryLock()) return@launch

            try {
                _uiState.value = _uiState.value.copy(inputText = "", isLoading = true)
                addMessage(ChatMessage(content = text, isUser = true))

                val response = aiOrchestrator.processInput(text)
                val responseMsg = ChatMessage(content = response, isUser = false)
                addMessage(responseMsg)
                _uiState.value = _uiState.value.copy(isLoading = false)

                // TTS: Speak exactly once for this specific response
                if (lastSpokenResponseId != responseMsg.id) {
                    lastSpokenResponseId = responseMsg.id
                    Log.d(TAG, "Speaking response ${responseMsg.id}")
                    ttsManager.speakQueued(response)
                } else {
                    Log.d(TAG, "Response ${responseMsg.id} already spoken, skipping TTS")
                }
            } finally {
                sendMutex.unlock()
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

    private fun addMessage(message: ChatMessage) {
        val last = _uiState.value.messages.lastOrNull()
        if (last != null && last.isUser == message.isUser && last.content.trim() == message.content.trim()) {
            return // Skip duplicate
        }

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
                val chatMessages = mutableListOf<ChatMessage>()
                for (entity in messages) {
                    val isUser = entity.role == "user"
                    val last = chatMessages.lastOrNull()
                    if (last != null && last.isUser == isUser && last.content.trim() == entity.content.trim()) {
                        continue // Drop duplicate historical records
                    }
                    chatMessages.add(
                        ChatMessage(
                            id = entity.id.toString(),
                            content = entity.content,
                            isUser = isUser,
                            timestamp = entity.timestamp,
                            toolName = entity.toolName,
                            toolResult = entity.toolResult
                        )
                    )
                }
                _uiState.value = _uiState.value.copy(messages = chatMessages)
            } else {
                _uiState.value = _uiState.value.copy(
                    messages = listOf(
                        ChatMessage(
                            content = "नमस्ते! मैं वासु हूँ, आपकी वॉइस असिस्टेंट। आज मैं आपकी क्या मदद करूँ?",
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
