package com.vasu.assistant.core.security

import kotlin.math.sqrt

/**
 * SpeakerEmbeddingGenerator - Shared utility for generating speaker embeddings.
 *
 * CRITICAL: This must be used by both VoiceEnrollmentManager and VoiceGuardian
 * to ensure embeddings generated during enrollment match those generated during
 * verification. Using different algorithms would cause verification to always fail.
 */
object SpeakerEmbeddingGenerator {

    const val EMBEDDING_SIZE = 128

    /**
     * Generate speaker embedding from audio data.
     *
     * @param audioData Raw audio samples (16kHz PCM float)
     * @return Normalized 128-dimensional embedding vector
     */
    fun generate(audioData: FloatArray): FloatArray {
        val embedding = FloatArray(EMBEDDING_SIZE)

        if (audioData.isEmpty()) return embedding

        val chunkSize = audioData.size / EMBEDDING_SIZE
        if (chunkSize <= 0) return embedding

        // Divide audio into chunks and compute RMS for each chunk
        for (i in 0 until EMBEDDING_SIZE) {
            var sum = 0f
            for (j in 0 until chunkSize) {
                val index = i * chunkSize + j
                if (index < audioData.size) {
                    sum += audioData[index] * audioData[index]
                }
            }
            embedding[i] = sqrt(sum / chunkSize)
        }

        // Normalize
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }
}
