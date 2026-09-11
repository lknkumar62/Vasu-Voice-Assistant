package com.vasu.assistant.core.security

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SpeakerVerifier @Inject constructor() {
    companion object {
        private const val TAG = "SpeakerVerifier"
        private const val SIMILARITY_THRESHOLD = 0.75f
    }

    fun verify(
        embedding: FloatArray,
        enrolledVoices: List<EnrolledVoice>
    ): VerificationResult {
        if (enrolledVoices.isEmpty()) {
            return VerificationResult.NoEnrolledVoices
        }

        var bestSimilarity = 0f
        var bestMatch: EnrolledVoice? = null

        for (voice in enrolledVoices) {
            if (voice.embedding.isEmpty()) continue
            val similarity = cosineSimilarity(embedding, voice.embedding)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = voice
            }
        }

        return if (bestSimilarity >= SIMILARITY_THRESHOLD && bestMatch != null) {
            Log.i(TAG, "Speaker verified: ${bestMatch.name} (similarity=$bestSimilarity)")
            VerificationResult.Verified(bestMatch)
        } else {
            Log.w(TAG, "Speaker unverified (best similarity=$bestSimilarity)")
            VerificationResult.Unverified(bestSimilarity)
        }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0f; var normA = 0f; var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = Math.sqrt((normA * normB).toDouble()).toFloat()
        return if (denom > 0f) dot / denom else 0f
    }
}

object SpeakerEmbeddingGenerator {
    fun generate(audioData: FloatArray): FloatArray {
        val embedding = FloatArray(128)
        val chunkSize = audioData.size / 128
        for (i in 0 until 128) {
            val start = i * chunkSize
            val end = minOf(start + chunkSize, audioData.size)
            if (start < end) {
                var sum = 0f
                for (j in start until end) sum += audioData[j] * audioData[j]
                embedding[i] = Math.sqrt((sum / (end - start)).toDouble()).toFloat()
            }
        }
        return embedding
    }
}

@Singleton
class PermissionGate @Inject constructor() {
    fun checkPermission(riskLevel: RiskLevel): PermissionResult {
        return when (riskLevel) {
            RiskLevel.LOW -> PermissionResult.Granted
            RiskLevel.MEDIUM -> PermissionResult.Granted
            RiskLevel.HIGH -> PermissionResult.Denied("High-risk action requires voice verification")
            RiskLevel.CRITICAL -> PermissionResult.Denied("Critical action requires boss verification")
        }
    }

    fun getCurrentSpeakerInfo(): String = "Speaker verification active"
}
