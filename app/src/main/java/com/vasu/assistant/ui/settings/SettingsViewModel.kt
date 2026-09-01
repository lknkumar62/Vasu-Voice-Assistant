package com.vasu.assistant.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.SecureKeyStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val language: String = "Hindi",
    val isDarkMode: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val keyStore: SecureKeyStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val apiKey = keyStore.retrieve("gemini_api_key") ?: ""
            _uiState.value = _uiState.value.copy(apiKey = apiKey)
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            keyStore.store("gemini_api_key", key)
            _uiState.value = _uiState.value.copy(apiKey = key)
        }
    }
}
