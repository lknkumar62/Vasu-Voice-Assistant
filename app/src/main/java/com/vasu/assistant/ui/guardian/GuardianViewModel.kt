package com.vasu.assistant.ui.guardian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.security.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianUiState(
    val isGuardianEnabled: Boolean = false,
    val guardianState: GuardianState = GuardianState.Disabled,
    val currentSpeaker: EnrolledVoice? = null,
    val enrolledVoices: List<EnrolledVoice> = emptyList(),
    val enrollmentState: EnrollmentState = EnrollmentState.Idle,
    val isEnrolling: Boolean = false,
    val enrollmentName: String = "",
    val enrollmentRole: UserRole = UserRole.FAMILY,
    val message: String = ""
)

@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val voiceGuardian: VoiceGuardian,
    private val roleManager: RoleManager,
    private val enrollmentManager: VoiceEnrollmentManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardianUiState())
    val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

    init {
        // Collect guardian state
        viewModelScope.launch {
            voiceGuardian.state.collect { state ->
                _uiState.value = _uiState.value.copy(guardianState = state)
            }
        }

        // Collect enrolled voices
        viewModelScope.launch {
            roleManager.enrolledVoices.collect { voices ->
                _uiState.value = _uiState.value.copy(enrolledVoices = voices)
            }
        }

        // Collect current speaker
        viewModelScope.launch {
            roleManager.currentSpeaker.collect { speaker ->
                _uiState.value = _uiState.value.copy(currentSpeaker = speaker)
            }
        }

        // Collect guardian enabled state
        viewModelScope.launch {
            roleManager.guardianEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(isGuardianEnabled = enabled)
            }
        }

        // Collect enrollment state
        viewModelScope.launch {
            enrollmentManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(enrollmentState = state)
                when (state) {
                    is EnrollmentState.Completed -> {
                        _uiState.value = _uiState.value.copy(
                            isEnrolling = false,
                            message = "Voice enrolled successfully!"
                        )
                    }
                    is EnrollmentState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            message = state.message
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun toggleGuardian() {
        if (_uiState.value.isGuardianEnabled) {
            voiceGuardian.disable()
        } else {
            voiceGuardian.enable()
        }
    }

    fun startEnrollment() {
        _uiState.value = _uiState.value.copy(isEnrolling = true)
        voiceGuardian.startEnrollment()
        viewModelScope.launch {
            for (i in 1..3) {
                kotlinx.coroutines.delay(1000)
                val sampleAudio = FloatArray(16000) { kotlin.math.sin(it * 0.1f) }
                enrollmentManager.recordSample(sampleAudio)
            }
        }
    }

    fun updateEnrollmentName(name: String) {
        _uiState.value = _uiState.value.copy(enrollmentName = name)
    }

    fun updateEnrollmentRole(role: UserRole) {
        _uiState.value = _uiState.value.copy(enrollmentRole = role)
    }

    fun completeEnrollment() {
        val name = _uiState.value.enrollmentName.trim()
        if (name.isEmpty()) {
            _uiState.value = _uiState.value.copy(message = "Please enter a name")
            return
        }
        voiceGuardian.completeEnrollment(name, _uiState.value.enrollmentRole)
    }

    fun cancelEnrollment() {
        enrollmentManager.cancelEnrollment()
        _uiState.value = _uiState.value.copy(isEnrolling = false)
    }

    fun removeVoice(id: String) {
        voiceGuardian.removeVoice(id)
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = "")
    }
}
