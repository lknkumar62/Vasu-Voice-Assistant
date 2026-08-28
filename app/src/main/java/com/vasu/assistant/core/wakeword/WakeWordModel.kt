package com.vasu.assistant.core.wakeword

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * WakeWordModel - TensorFlow Lite model for wake word detection.
 *
 * Loads and runs inference on a pre-trained wake word detection model.
 * Model expects mel spectrogram features and outputs a probability score.
 */
class WakeWordModel(
    private val context: Context,
    private val modelPath: String = "wakeword/hello_vasu.tflite"
) {
    private var interpreter: Interpreter? = null
    private var isLoaded = false

    // Model configuration
    val inputSize: Int = 98          // Number of mel spectrogram frames
    val numMelBands: Int = 40        // Number of mel bands
    val threshold: Float = 0.7f      // Detection threshold

    /**
     * Load the TFLite model
     */
    fun load(): Boolean {
        return try {
            val model = loadModelFile()
            interpreter = Interpreter(model)
            isLoaded = true
            true
        } catch (e: Exception) {
            isLoaded = false
            false
        }
    }

    /**
     * Run inference on mel spectrogram features
     * @param features Mel spectrogram features [inputSize][numMelBands]
     * @return Detection score (0.0 to 1.0)
     */
    fun predict(features: Array<FloatArray>): Float {
        if (!isLoaded || interpreter == null) {
            return 0f
        }

        return try {
            // Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(
                inputSize * numMelBands * 4  // 4 bytes per float
            ).apply {
                order(ByteOrder.nativeOrder())
            }

            // Fill input buffer
            for (i in 0 until inputSize) {
                for (j in 0 until numMelBands) {
                    inputBuffer.putFloat(features[i][j])
                }
            }

            // Prepare output buffer
            val outputBuffer = ByteBuffer.allocateDirect(4).apply {
                order(ByteOrder.nativeOrder())
            }

            // Run inference
            interpreter?.run(inputBuffer, outputBuffer)

            // Get result
            outputBuffer.rewind()
            outputBuffer.float
        } catch (e: Exception) {
            0f
        }
    }

    /**
     * Check if wake word is detected
     * @param features Mel spectrogram features
     * @return true if wake word detected
     */
    fun detect(features: Array<FloatArray>): Boolean {
        val score = predict(features)
        return score >= threshold
    }

    /**
     * Close the interpreter
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        isLoaded = false
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }
}
