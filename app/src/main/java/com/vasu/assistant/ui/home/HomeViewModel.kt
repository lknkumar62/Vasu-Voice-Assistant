package com.vasu.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isThinking: Boolean = false,
    val lastMessage: String = "",
    val isWakeWordActive: Boolean = false,
    val wakeWordState: WakeWordState = WakeWordState.IDLE,
    val serviceState: VasuServiceState = VasuServiceState.OFF
)

enum class VasuServiceState { OFF, STARTING, LISTENING, ACTIVE, SPEAKING, ERROR }

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VasuSettings,
    private val wakeWordDetector: WakeWordDetector
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settings.wakeWordEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isWakeWordActive = enabled)
            }
        }
        viewModelScope.launch {
            wakeWordDetector.state.collect { state ->
                _uiState.value = _uiState.value.copy(wakeWordState = state)
            }
        }
        viewModelScope.launch {
            VasuForegroundService.serviceState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    serviceState = when (state) {
                        com.vasu.assistant.core.service.VasuServiceState.OFF -> VasuServiceState.OFF
                        com.vasu.assistant.core.service.VasuServiceState.STARTING -> VasuServiceState.STARTING
                        com.vasu.assistant.core.service.VasuServiceState.LISTENING -> VasuServiceState.LISTENING
                        com.vasu.assistant.core.service.VasuServiceState.ACTIVATING,
                        com.vasu.assistant.core.service.VasuServiceState.ACTIVE -> VasuServiceState.ACTIVE
                        com.vasu.assistant.core.service.VasuServiceState.SPEAKING -> VasuServiceState.SPEAKING
                        com.vasu.assistant.core.service.VasuServiceState.STOPPING,
                        com.vasu.assistant.core.service.VasuServiceState.ERROR -> VasuServiceState.ERROR
                    },
                    isListening = state == com.vasu.assistant.core.service.VasuServiceState.LISTENING,
                    isSpeaking = state == com.vasu.assistant.core.service.VasuServiceState.SPEAKING
                )
            }
        }
    }

    fun toggleWakeWord() {
        val enabled = !settings.wakeWordEnabled.value
        settings.setWakeWordEnabled(enabled)
        if (enabled) {
            wakeWordDetector.initialize()
            VasuForegroundService.start(context)
        } else {
            VasuForegroundService.stop(context)
            wakeWordDetector.stop()
        }
    }
}
