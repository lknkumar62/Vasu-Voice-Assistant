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
     * Process enrollment - generate embedding and save
     */
    private fun processEnrollment() {
        _state.value = EnrollmentState.Processing(collectedSamples.size)

        // Generate embedding (simplified - in real app, use speaker encoder model)
        val embedding = generateEmbedding(collectedSamples)

        // Store the embedding temporarily
        _state.value = EnrollmentState.SampleRecorded(collectedSamples.size)
    }

    /**
     * Complete enrollment with name and role
     */
    fun completeEnrollment(name: String, role: UserRole) {
        if (collectedSamples.size < requiredSamples) {
            _state.value = EnrollmentState.Error("Not enough samples collected.")
            return
        }

        val embedding = generateEmbedding(collectedSamples)
        val voice = roleManager.enrollVoice(name, role, embedding)

        collectedSamples.clear()
        _state.value = EnrollmentState.Completed(voice)
    }

    /**
     * Generate speaker embedding from samples
     * This is a simplified version - real implementation would use
     * a speaker encoder neural network (Phase 4 enhancement)
     */
    private fun generateEmbedding(samples: List<FloatArray>): FloatArray {
        // Simplified: Average of sample features
        val embeddingSize = 128
        val embedding = FloatArray(embeddingSize)

        for (sample in samples) {
            // Extract simple features (energy, zero crossings, etc.)
            val features = extractSimpleFeatures(sample)
            for (i in 0 until minOf(features.size, embeddingSize)) {
                embedding[i] += features[i]
            }
        }

        // Average
        for (i in embedding.indices) {
            embedding[i] /= samples.size.toFloat()
        }

        // Normalize
        val norm = kotlin.math.sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }

    /**
     * Extract simple audio features
     */
    private fun extractSimpleFeatures(audio: FloatArray): FloatArray {
        val features = mutableListOf<Float>()

        // Energy
        val energy = audio.sumOf { (it * it).toDouble() } / audio.size
        features.add(energy.toFloat())

        // Zero crossing rate
        var zcr = 0
        for (i in 1 until audio.size) {
            if ((audio[i] >= 0 && audio[i - 1] < 0) || (audio[i] < 0 && audio[i - 1] >= 0)) {
                zcr++
            }
        }
        features.add(zcr.toFloat() / audio.size)

        // Spectral centroid (simplified)
        val halfSize = audio.size / 2
        var weightedSum = 0f
        var totalMagnitude = 0f
        for (i in 0 until halfSize) {
            val magnitude = kotlin.math.abs(audio[i])
            weightedSum += i * magnitude
            totalMagnitude += magnitude
        }
        features.add(if (totalMagnitude > 0) weightedSum / totalMagnitude else 0f)

        // Pad to 128 features
        while (features.size < 128) {
            features.add(0f)
        }

        return features.toFloatArray()
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
