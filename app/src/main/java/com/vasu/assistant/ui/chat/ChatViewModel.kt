package com.vasu.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isListening: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Welcome message
        addMessage(
            ChatMessage(
                content = "Hello! I am VASU, your voice assistant. How can I help you today?",
                isUser = false
            )
        )
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
            addMessage(
                ChatMessage(
                    content = "I received your message: \"$text\"\n\nAI engine will be connected in Phase 6. Stay tuned!",
                    isUser = false
                )
            )
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun toggleListening() {
        _uiState.value = _uiState.value.copy(
            isListening = !_uiState.value.isListening
        )
    }

    private fun addMessage(message: ChatMessage) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }
}
