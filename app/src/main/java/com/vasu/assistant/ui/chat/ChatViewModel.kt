package com.vasu.assistant.ui.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.stt.STTState
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.database.ConversationDao
import com.vasu.assistant.database.ConversationMessageEntity
import com.vasu.assistant.database.ConversationSessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

private val chatMessageIdSeq = AtomicLong()

data class ChatMessage(
    val id: String = "${System.currentTimeMillis()}-${chatMessageIdSeq.incrementAndGet()}",
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
    private val conversationDao: ConversationDao
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** Saved chat sessions, newest first (FIX-5). */
    val sessions: Flow<List<ConversationSessionEntity>> = conversationDao.observeSessions()

    /** Current session row id; null until the first message of a new chat. */
    private var currentSessionId: String? = null

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

            // Transient streaming bubble id (hoisted so the error path can clean it up).
            var streamingId: String? = null

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

                val sessionId = ensureSession()
                persistAndAdd(ChatMessage(content = text, isUser = true), sessionId)

                val requestId = "req_${System.currentTimeMillis()}_${text.hashCode()}"
                Log.d(TAG, "AI_REQUEST requestId=$requestId chatTurn")

                // Word-stream: the assistant bubble grows sentence by sentence as
                // the orchestrator emits them (the same units TTS speaks).
                val response = aiOrchestrator.processInputStreaming(text, history) { sentence ->
                    val open = streamingId
                    if (open == null) {
                        val placeholder = ChatMessage(content = sentence, isUser = false)
                        streamingId = placeholder.id
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages + placeholder
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            messages = _uiState.value.messages.map { m ->
                                if (m.id == open) m.copy(content = m.content + " " + sentence) else m
                            }
                        )
                    }
                    ttsManager.speakQueued(sentence)
                }

                // Swap the transient streaming bubble for the persisted final reply.
                streamingId?.let { open ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == open }
                    )
                }
                val responseMsg = persistAndAdd(ChatMessage(content = response, isUser = false), sessionId)
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
                // Drop any un-persisted streaming bubble so no ghost reply stays.
                streamingId?.let { open ->
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages.filterNot { it.id == open }
                    )
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                addMessage(
                    ChatMessage(
                        content = "Sorry, I couldn't process that: ${e.message ?: "unknown error"}",
                        isUser = false
                    )
                )
                com.vasu.assistant.core.logging.ErrorLog.log("CHAT", "sendMessage failed: ${e.message}", e)
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

    /** Clears the CURRENT session (history rows + session row) and starts fresh. */
    fun clearChat() {
        viewModelScope.launch {
            val sessionId = currentSessionId
            if (sessionId != null) {
                try {
                    conversationDao.deleteConversation(sessionId)
                    conversationDao.deleteSession(sessionId)
                } catch (_: Exception) {}
            }
            currentSessionId = null
            lastSpokenResponseId = null
            _uiState.value = _uiState.value.copy(messages = greetingList())
        }
    }

    /** Fire-and-forget variant used by non-send paths (STT errors). */
    private fun addMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                persistAndAdd(message, ensureSession())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // DB unavailable — still surface the message in this session.
                Log.e(TAG, "addMessage failed", e)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + message
                )
            }
        }
    }

    /** Persists a message (with adjacency dedup) and appends it with its DB row id as UI id. */
    private suspend fun persistAndAdd(message: ChatMessage, sessionId: String): ChatMessage {
        val last = _uiState.value.messages.lastOrNull()
        if (last != null && last.isUser == message.isUser && last.content.trim() == message.content.trim()) {
            return last // Skip duplicate
        }

        var stored = message
        try {
            val rowId = conversationDao.insertMessage(
                ConversationMessageEntity(
                    conversationId = sessionId,
                    role = if (message.isUser) "user" else "assistant",
                    content = message.content,
                    toolName = message.toolName,
                    toolResult = message.toolResult,
                    timestamp = message.timestamp,
                    source = "chat",
                    isToolExecution = message.isToolExecution
                )
            )
            stored = message.copy(id = rowId.toString())
            if (message.isUser) {
                // The first user message names the session; every turn bumps it.
                if (conversationDao.getMessageCount(sessionId) == 1) {
                    conversationDao.renameSession(sessionId, ChatTextFormatter.deriveTitle(message.content))
                }
                conversationDao.touchSession(sessionId, System.currentTimeMillis())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "insertMessage failed", e)
        }

        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + stored
        )
        return stored
    }

    /** Session row for the current chat, created on demand. */
    private suspend fun ensureSession(): String {
        currentSessionId?.let { return it }
        val now = System.currentTimeMillis()
        val id = "s_${now}_${chatMessageIdSeq.incrementAndGet()}"
        conversationDao.upsertSession(
            ConversationSessionEntity(id = id, title = "New chat", createdAt = now, updatedAt = now)
        )
        currentSessionId = id
        return id
    }

    /** Starts a fresh unsaved chat (session row is created on first message). */
    fun newChat() {
        viewModelScope.launch {
            try { ttsManager.stop() } catch (_: Exception) {}
            currentSessionId = null
            lastSpokenResponseId = null
            _uiState.value = _uiState.value.copy(messages = greetingList())
        }
    }

    /** Switches to a saved session and loads its messages. */
    fun openSession(sessionId: String) {
        viewModelScope.launch {
            try {
                try { ttsManager.stop() } catch (_: Exception) {}
                val loaded = buildMessages(conversationDao.getMessages(sessionId).first())
                currentSessionId = sessionId
                lastSpokenResponseId = null
                _uiState.value = _uiState.value.copy(
                    messages = if (loaded.isEmpty()) greetingList() else loaded
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "openSession failed", e)
            }
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                conversationDao.renameSession(sessionId, clean)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "renameSession failed", e)
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                conversationDao.deleteConversation(sessionId)
                conversationDao.deleteSession(sessionId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteSession failed", e)
            }
            if (currentSessionId == sessionId) {
                currentSessionId = null
                lastSpokenResponseId = null
                _uiState.value = _uiState.value.copy(messages = greetingList())
            }
        }
    }

    /** Deletes one persisted message (long-press menu). Ids are DB row ids. */
    fun deleteMessage(messageId: String) {
        val rowId = messageId.toLongOrNull() ?: return
        viewModelScope.launch {
            try {
                conversationDao.deleteMessage(rowId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "deleteMessage failed", e)
            }
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.filterNot { it.id == messageId }
            )
        }
    }

    /** Plain-text export of the current session for the share sheet. */
    fun exportText(): String = ChatTextFormatter.toPlainText(_uiState.value.messages)

    private fun loadConversationHistory() {
        viewModelScope.launch {
            try {
                val latest = conversationDao.getLatestSession()
                if (latest == null) {
                    currentSessionId = null
                    _uiState.value = _uiState.value.copy(messages = greetingList())
                } else {
                    currentSessionId = latest.id
                    val loaded = buildMessages(conversationDao.getMessages(latest.id).first())
                    _uiState.value = _uiState.value.copy(
                        messages = if (loaded.isEmpty()) greetingList() else loaded
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "history load failed", e)
                _uiState.value = _uiState.value.copy(messages = greetingList())
            }
        }
    }

    private fun buildMessages(entities: List<ConversationMessageEntity>): List<ChatMessage> {
        val chatMessages = mutableListOf<ChatMessage>()
        for (entity in entities) {
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
                    isToolExecution = entity.isToolExecution,
                    toolName = entity.toolName,
                    toolResult = entity.toolResult
                )
            )
        }
        return chatMessages
    }

    private fun greetingList(): List<ChatMessage> = listOf(
        ChatMessage(
            content = "नमस्ते! मैं वासु हूँ, आपकी वॉइस असिस्टेंट। आज मैं आपकी क्या मदद करूँ?",
            isUser = false
        )
    )

    override fun onCleared() {
        super.onCleared()
        try { sttManager.stopListening() } catch (_: Exception) {}
        try { ttsManager.stop() } catch (_: Exception) {}
    }
}
