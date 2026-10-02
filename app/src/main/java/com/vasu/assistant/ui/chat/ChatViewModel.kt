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
        try {
            ttsManager.initialize()
        } catch (e: Exception) {
            Log.e(TAG, "ttsManager.initialize failed", e)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatMessage(
                    content = "System: Voice setup unavailable",
                    isUser = false,
                    toolName = "system"
                )
            )
        }

        // Load chat history — does NOT trigger TTS
        try {
            loadConversationHistory()
        } catch (e: Exception) {
            Log.e(TAG, "loadConversationHistory failed", e)
        }

        viewModelScope.launch {
            try {
                sttManager.state.collect { sttState ->
                    _uiState.value = _uiState.value.copy(
                        isListening = sttState == STTState.LISTENING
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "stt state collector failed", e)
            }
        }

        viewModelScope.launch {
            try {
                sttManager.partialResults.collect { transcript ->
                    _uiState.value = _uiState.value.copy(partialTranscript = transcript)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "stt partial collector failed", e)
            }
        }

        // STT final results — do NOT auto-send. User must explicitly press Send or Mic.
        // Only update the input field so user can review before sending.
        viewModelScope.launch {
            try {
                sttManager.results.collect { result ->
                    if (result.isFinal && result.text.isNotBlank()) {
                        Log.d(TAG, "STT final result: ${result.text}")
                        _uiState.value = _uiState.value.copy(partialTranscript = "")
                        // Just fill the input field — do NOT auto-send
                        updateInput(result.text)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "stt results collector failed", e)
            }
        }

        viewModelScope.launch {
            try {
                sttManager.errors.collect { error ->
                    addMessage(ChatMessage(content = error.message, isUser = false))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "stt errors collector failed", e)
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

                // Prior turns only (this turn rides in as the prompt). Capped at
                // the last 8 messages so prompt size — and latency — stay bounded.
                val history = _uiState.value.messages.takeLast(8).map { m ->
                    com.vasu.assistant.core.ai.ChatMessage(
                        role = if (m.isUser) "user" else "assistant",
                        content = m.content
                    )
                }

                addMessage(ChatMessage(content = text, isUser = true))

                val requestId = "req_${System.currentTimeMillis()}_${text.hashCode()}"
                Log.d(TAG, "AI_REQUEST requestId=$requestId chatTurn")
                val response = aiOrchestrator.processInputStreaming(text, history) { sentence ->
                    ttsManager.speakQueued(sentence)
                }
                val responseMsg = ChatMessage(content = response, isUser = false)
                addMessage(responseMsg)
                _uiState.value = _uiState.value.copy(isLoading = false)

                // TTS: sentence-level streaming enqueue — each completed
                // sentence is spoken immediately (SpeechQueue FIFO preserves
                // order); the full response is still logged exactly once.
                if (lastSpokenResponseId != responseMsg.id) {
                    lastSpokenResponseId = responseMsg.id
                    Log.d(TAG, "AI_RESPONSE_COMPLETE requestId=$requestId assistantMessageId=${responseMsg.id}")
                    Log.d(TAG, "GEMINI_TTS_REQUEST assistantMessageId=${responseMsg.id} (sentence stream)")
                } else {
                    Log.d(TAG, "Response ${responseMsg.id} already spoken, skipping TTS")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sendMessage failed", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
                addMessage(
                    ChatMessage(
                        content = "Sorry, I couldn't process that: ${e.message ?: "unknown error"}",
                        isUser = false
                    )
                )
            } finally {
                sendMutex.unlock()
            }
        }
    }

    fun toggleListening() {
        try {
            if (_uiState.value.isListening) {
                sttManager.stopListening()
            } else {
                sttManager.startListening()
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleListening failed", e)
        }
    }

    fun stopSpeaking() {
        try {
            ttsManager.stop()
        } catch (e: Exception) {
            Log.e(TAG, "stopSpeaking failed", e)
        }
    }

    /**
     * Replay a specific assistant message on demand (copy/speak button).
     * Does NOT update lastSpokenResponseId — this is user-initiated replay,
     * separate from the event-driven sendMessage() TTS path.
     */
    fun replayMessage(text: String) {
        if (text.isBlank()) return
        try {
            ttsManager.speakQueued(text)
        } catch (e: Exception) {
            Log.e(TAG, "replayMessage failed", e)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            try {
                conversationDao.deleteAllMessages()
            } catch (_: Exception) {}
            lastSpokenResponseId = null
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
            try {
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "insertMessage failed", e)
            }
        }
    }

    private fun loadConversationHistory() {
        viewModelScope.launch {
            try {
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
                _uiState.value = _uiState.value.copy(messages = chatMessages                )
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "history load failed", e)
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
        try { sttManager.stopListening() } catch (_: Exception) {}
        try { ttsManager.stop() } catch (_: Exception) {}
    }
}
