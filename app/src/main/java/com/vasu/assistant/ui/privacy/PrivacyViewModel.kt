package com.vasu.assistant.ui.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.notifications.NotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationData(
    val title: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PrivacyUiState(
    val notifications: List<NotificationData> = emptyList(),
    val otpProtectionEnabled: Boolean = true
)

@HiltViewModel
class PrivacyViewModel @Inject constructor(
    private val notificationListener: NotificationListener
) : ViewModel() {
    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    fun toggleOtpProtection(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(otpProtectionEnabled = enabled)
    }
}
