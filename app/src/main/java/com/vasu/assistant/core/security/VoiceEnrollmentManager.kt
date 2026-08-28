package com.vasu.assistant.core.security

import android.content.Context
import com.vasu.assistant.core.stt.STTManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enrollment state
 */
sealed class EnrollmentState {
    data object Idle : EnrollmentState()
    data object Recording : EnrollmentState()
    data class SampleRecorded(val sampleCount: Int) : EnrollmentState()
    data class Processing(val sampleCount: Int) : EnrollmentState()
    data class Completed(val voice: EnrolledVoice) : EnrollmentState()
    data class Error(val message: String) : EnrollmentState()
}

/**
 * VoiceEnrollmentManager - Manages voice enrollment process.
 *
 * Enrollment Flow:
 * 1. Start enrollment
 * 2. Record multiple short samples
 * 3. Reject samples with excessive noise
 * 4. Generate speaker embeddings
 * 5. Store encrypted embeddings
 * 6. Assign role
 * 7. Confirm enrollment
 */
@Singleton
class VoiceEnrollmentManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleManager: RoleManager,
    private val sttManager: STTManager
) {
    private val _state = MutableStateFlow<EnrollmentState>(EnrollmentState.Idle)
    val state: StateFlow<EnrollmentState> = _state.asStateFlow()

    private val requiredSamples = 3
    private val minSampleLength = 1000L  // 1 second
    private val maxSampleLength = 5000L  // 5 seconds

    private val collectedSamples = mutableListOf<FloatArray>()

    /**
     * Start enrollment process
     */
    fun startEnrollment() {
        collectedSamples.clear()
        _state.value = EnrollmentState.Recording
    }

    /**
     * Record a voice sample
     * @param audioData Audio sample data
     */
    fun recordSample(audioData: FloatArray) {
        if (_state.value !is EnrollmentState.Recording) return

        // Validate sample length
        val durationMs = (audioData.size / 16000.0) * 1000
        if (durationMs < minSampleLength) {
            _state.value = EnrollmentState.Error("Sample too short. Speak for at least 1 second.")
            return
        }

        if (durationMs > maxSampleLength) {
            _state.value = EnrollmentState.Error("Sample too long. Keep it under 5 seconds.")
            return
        }

        // Check for excessive noise
        val snr = calculateSNR(audioData)
        if (snr < 10f) {
            _state.value = EnrollmentState.Error("Too much background noise. Try again.")
            return
        }

        // Add sample
        collectedSamples.add(audioData)
        _state.value = EnrollmentState.SampleRecorded(collectedSamples.size)

        // Check if we have enough samples
        if (collectedSamples.size >= requiredSamples) {
            processEnrollment()
        }
    }

    /**
     * Cancel enrollment
     */
    fun cancelEnrollment() {
        collectedSamples.clear()
        _state.value = EnrollmentState.Idle
    }

    /**
     * Process enrollment - validate samples and prepare for completion
     */
    private fun processEnrollment() {
        _state.value = EnrollmentState.Processing(collectedSamples.size)

        // Validate we have enough samples
        if (collectedSamples.size < requiredSamples) {
            _state.value = EnrollmentState.Error("Not enough samples collected.")
            return
        }
    }

    /**
     * Complete enrollment with name and role
     */
    fun completeEnrollment(name: String, role: UserRole) {
        if (collectedSamples.size < requiredSamples) {
            _state.value = EnrollmentState.Error("Not enough samples collected.")
            return
        }

        // Generate a single combined embedding from all samples using shared generator
        val combinedAudio = collectedSamples.flatMap { it.toList() }.toFloatArray()
        val embedding = SpeakerEmbeddingGenerator.generate(combinedAudio)
        val voice = roleManager.enrollVoice(name, role, embedding)

        collectedSamples.clear()
        _state.value = EnrollmentState.Completed(voice)
    }

    /**
     * Calculate Signal-to-Noise Ratio
     */
    private fun calculateSNR(audio: FloatArray): Float {
        // Simple SNR estimation
        val signalEnergy = audio.take(audio.size / 4).sumOf { (it * it).toDouble() } / (audio.size / 4)
        val noiseEnergy = audio.takeLast(audio.size / 4).sumOf { (it * it).toDouble() } / (audio.size / 4)

        return if (noiseEnergy > 0) {
            (10 * kotlin.math.log10(signalEnergy / noiseEnergy)).toFloat()
        } else {
            100f  // Very high SNR
        }
    }
}
