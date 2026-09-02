package com.vasu.assistant.core.security

import com.vasu.assistant.core.stt.STTManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guardian state
 */
sealed class GuardianState {
    data object Disabled : GuardianState()
    data object Listening : GuardianState()
    data object Verifying : GuardianState()
    data class Verified(val speaker: EnrolledVoice) : GuardianState()
    data class Unverified(val reason: String) : GuardianState()
    data class Error(val message: String) : GuardianState()
}

/**
 * VoiceGuardian - Main coordinator for voice verification system.
 *
 * Pipeline:
 * 1. VAD (Voice Activity Detection)
 * 2. Speech segment capture
 * 3. Speaker embedding generation
 * 4. Cosine similarity comparison
 * 5. Threshold check
 * 6. Speaker identity determination
 * 7. Role assignment
 * 8. Permission gate check
 */
@Singleton
class VoiceGuardian @Inject constructor(
    private val roleManager: RoleManager,
    private val speakerVerifier: SpeakerVerifier,
    private val enrollmentManager: VoiceEnrollmentManager,
    private val permissionGate: PermissionGate,
    private val sttManager: STTManager
) {
    private val _state = MutableStateFlow<GuardianState>(GuardianState.Disabled)
    val state: StateFlow<GuardianState> = _state.asStateFlow()

    /**
     * Enable Voice Guardian
     */
    fun enable() {
        roleManager.setGuardianEnabled(true)
        _state.value = GuardianState.Listening
    }

    /**
     * Disable Voice Guardian
     */
    fun disable() {
        roleManager.setGuardianEnabled(false)
        roleManager.setCurrentSpeaker(null)
        _state.value = GuardianState.Disabled
    }

    /**
     * Check if guardian is enabled
     */
    fun isEnabled(): Boolean = roleManager.guardianEnabled.value

    /**
     * Process audio for speaker verification
     * @param audioData Audio data from microphone
     */
    fun processAudio(audioData: FloatArray) {
        if (!roleManager.guardianEnabled.value) return

        _state.value = GuardianState.Verifying

        // Generate embedding from audio using shared generator
        val embedding = SpeakerEmbeddingGenerator.generate(audioData)

        // Verify against enrolled voices
        val result = speakerVerifier.verify(embedding)

        when (result) {
            is VerificationResult.Verified -> {
                _state.value = GuardianState.Verified(result.speaker)
            }
            is VerificationResult.Unverified -> {
                _state.value = GuardianState.Unverified(
                    "Voice not recognized. Similarity: ${(result.similarity * 100).toInt()}%"
                )
            }
            is VerificationResult.NoEnrolledVoices -> {
                _state.value = GuardianState.Error("No voices enrolled. Please enroll first.")
            }
        }
    }

    /**
     * Check if a command is allowed for current speaker
     */
    fun checkCommandPermission(command: String = "", riskLevel: RiskLevel): Boolean {
        if (!roleManager.guardianEnabled.value) return true

        Log.d(TAG, "Checking permission for command '$command' at risk level $riskLevel")
        val result = permissionGate.checkPermission(riskLevel)
        return result is PermissionResult.Granted
    }

    /**
     * Get current speaker info
     */
    fun getCurrentSpeakerInfo(): String {
        return permissionGate.getCurrentSpeakerInfo()
    }

    /**
     * Start enrollment process
     */
    fun startEnrollment() {
        enrollmentManager.startEnrollment()
    }

    /**
     * Enroll a voice sample
     */
    fun enrollSample(audioData: FloatArray) {
        enrollmentManager.recordSample(audioData)
    }

    /**
     * Complete enrollment
     */
    fun completeEnrollment(name: String, role: UserRole) {
        enrollmentManager.completeEnrollment(name, role)
    }

    /**
     * Remove enrolled voice
     */
    fun removeVoice(id: String): Boolean {
        return roleManager.removeVoice(id)
    }

    /**
     * List enrolled voices
     */
    fun listVoices(): List<EnrolledVoice> {
        return roleManager.listVoices()
    }
}
