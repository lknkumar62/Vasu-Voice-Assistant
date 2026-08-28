package com.vasu.assistant.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * SpeakerVerifier - Verifies speaker identity using voice embeddings.
 *
 * Uses cosine similarity to compare speaker embeddings
 * and determine if the current speaker matches an enrolled voice.
 */
@Singleton
class SpeakerVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val roleManager: RoleManager
) {
    private val _verificationState = MutableStateFlow<VerificationState>(VerificationState.Idle)
    val verificationState: StateFlow<VerificationState> = _verificationState.asStateFlow()

    // Verification threshold
    private val similarityThreshold = 0.75f

    /**
     * Verify speaker identity against enrolled voices
     * @param embedding Speaker embedding to verify
     * @return Verification result
     */
    fun verify(embedding: FloatArray): VerificationResult {
        _verificationState.value = VerificationState.Verifying

        val enrolledVoices = roleManager.enrolledVoices.value

        if (enrolledVoices.isEmpty()) {
            _verificationState.value = VerificationState.NoEnrolledVoices
            return VerificationResult.NoEnrolledVoices
        }

        // Find best match
        var bestMatch: EnrolledVoice? = null
        var bestSimilarity = 0f

        for (voice in enrolledVoices) {
            val similarity = cosineSimilarity(embedding, voice.embedding)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = voice
            }
        }

        return if (bestMatch != null && bestSimilarity >= similarityThreshold) {
            // Verified!
            roleManager.setCurrentSpeaker(bestMatch)
            roleManager.recordVerification(bestMatch.id)

            _verificationState.value = VerificationState.Verified(
                speaker = bestMatch,
                confidence = bestSimilarity
            )

            VerificationResult.Verified(
                speaker = bestMatch,
                confidence = bestSimilarity
            )
        } else {
            // Not verified
            _verificationState.value = VerificationState.Unverified(
                bestSimilarity
            )

            VerificationResult.Unverified(
                bestMatch?.name ?: "Unknown",
                bestSimilarity
            )
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return if (normA > 0f && normB > 0f) {
            dotProduct / (sqrt(normA) * sqrt(normB))
        } else {
            0f
        }
    }

    /**
     * Reset verification state
     */
    fun reset() {
        _verificationState.value = VerificationState.Idle
    }
}

/**
 * Verification states
 */
sealed class VerificationState {
    data object Idle : VerificationState()
    data object Verifying : VerificationState()
    data object NoEnrolledVoices : VerificationState()
    data class Verified(
        val speaker: EnrolledVoice,
        val confidence: Float
    ) : VerificationState()
    data class Unverified(
        val bestSimilarity: Float
    ) : VerificationState()
}

/**
 * Verification results
 */
sealed class VerificationResult {
    data object NoEnrolledVoices : VerificationResult()
    data class Verified(
        val speaker: EnrolledVoice,
        val confidence: Float
    ) : VerificationResult()
    data class Unverified(
        val closestName: String,
        val similarity: Float
    ) : VerificationResult()
}
