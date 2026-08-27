package com.vasu.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val lastMessage: String = "Hello! I am VASU. How can I help you?",
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
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun toggleListening() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isListening = !_uiState.value.isListening,
                isSpeaking = false,
                isThinking = false
            )
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
}
