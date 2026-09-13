package com.vasu.assistant.core.guardian

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * VoiceGuardian - Biometric Speaker Verification Pipeline.
 * 
 * Pipeline:
 * 1. VAD (Silero) -> Ensure audio window contains speech.
 * 2. Embedding (ECAPA-TDNN) -> Generate speaker vector.
 * 3. Similarity (Cosine) -> Compare against reference cohort.bin.
 */
@Singleton
class VoiceGuardian @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "VoiceGuardian"
        private const val VAD_MODEL_PATH = "guardian/silero_vad.tflite"
        private const val EMBED_MODEL_PATH = "guardian/ecapa6s.tflite"
        private const val COHORT_PATH = "guardian/cohort.bin"
        private const val SIMILARITY_THRESHOLD = 0.75f
    }

    private var vadInterpreter: Interpreter? = null
    private var embedInterpreter: Interpreter? = null
    private var ownerEmbedding: FloatArray? = null

    init {
        try {
            vadInterpreter = Interpreter(loadModelFile(VAD_MODEL_PATH))
            embedInterpreter = Interpreter(loadModelFile(EMBED_MODEL_PATH))
            ownerEmbedding = loadReferenceEmbedding(COHORT_PATH)
            Log.i(TAG, "VoiceGuardian initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoiceGuardian: ${e.message}")
        }
    }

    /**
     * Verifies if the provided audio data belongs to the registered owner.
     * @param audioData Normalized FloatArray of audio samples.
     * @return True if the speaker is verified as the owner.
     */
    fun verifyOwner(audioData: FloatArray): Boolean {
        if (vadInterpreter == null || embedInterpreter == null || ownerEmbedding == null) {
            Log.e(TAG, "Guardian models not loaded. Verification failed.")
            return false
        }

        return try {
            // 1. Voice Activity Detection (VAD)
            if (!hasSpeech(audioData)) {
                Log.d(TAG, "VoiceGuardian: No speech detected in window")
                return false
            }

            // 2. Generate Speaker Embedding
            val embedding = generateEmbedding(audioData)

            // 3. Cosine Similarity
            val similarity = cosineSimilarity(embedding, ownerEmbedding!!)
            Log.i(TAG, "VoiceGuardian: Speaker similarity = $similarity (Threshold: $SIMILARITY_THRESHOLD)")
            
            similarity >= SIMILARITY_THRESHOLD
        } catch (e: Exception) {
            Log.e(TAG, "Error during owner verification: ${e.message}")
            false
        }
    }

    private fun hasSpeech(audioData: FloatArray): Boolean {
        val input = ByteBuffer.allocateDirect(audioData.size * 4).apply {
            order(ByteOrder.nativeOrder())
            audioData.forEach { putFloat(it) }
        }
        val output = Array(1) { FloatArray(1) }
        vadInterpreter?.run(input, output)
        return output[0][0] > 0.5f
    }

    private fun generateEmbedding(audioData: FloatArray): FloatArray {
        val input = ByteBuffer.allocateDirect(audioData.size * 4).apply {
            order(ByteOrder.nativeOrder())
            audioData.forEach { putFloat(it) }
        }
        // ECAPA-TDNN typically outputs a 192-dimensional embedding
        val output = Array(1) { FloatArray(192) }
        embedInterpreter?.run(input, output)
        return output[0]
    }

    private fun cosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }
        return if (normA == 0f || normB == 0f) 0f else dotProduct / (sqrt(normA) * sqrt(normB))
    }

    private fun loadModelFile(path: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(path)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadReferenceEmbedding(path: String): FloatArray? {
        return try {
            val bytes = context.assets.open(path).readBytes()
            val floatBuffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val embedding = FloatArray(floatBuffer.remaining())
            floatBuffer.get(embedding)
            Log.d(TAG, "Loaded reference embedding from $path")
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load reference embedding: ${e.message}")
            null
        }
    }
}
