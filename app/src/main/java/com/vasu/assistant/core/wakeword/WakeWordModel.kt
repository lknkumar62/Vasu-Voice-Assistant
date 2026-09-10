package com.vasu.assistant.core.wakeword

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Why the wake word model is or is not usable.
 *
 * load() used to return a bare Boolean and swallow the exception, which made a
 * missing asset indistinguishable from a corrupt file or a shape mismatch - three
 * problems with three different fixes. The detail strings are shown to the user.
 */
enum class ModelStatus(val detail: String) {
    NOT_LOADED("The wake word model has not been loaded yet."),
    READY("Wake word model loaded."),
    ASSET_MISSING("hello_vasu.tflite is not bundled in this build, so \"Hello Vasu\" cannot be detected. Use the voice button instead."),
    LOAD_FAILED("The wake word model file is present but TensorFlow Lite could not read it."),
    INFERENCE_FAILED("The wake word model rejected its input, so its shape does not match this build.")
}

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

    /** Why the model is unusable, for the notification and the Settings screen. */
    var status: ModelStatus = ModelStatus.NOT_LOADED
        private set

    // Model configuration
    val inputSize: Int = 98          // Number of mel spectrogram frames
    val numMelBands: Int = 40        // Number of mel bands
    val threshold: Float = 0.7f      // Detection threshold

    /**
     * Load the TFLite model, reporting why when it cannot be loaded.
     */
    fun load(): ModelStatus {
        status = try {
            interpreter = Interpreter(loadModelFile())
            ModelStatus.READY
        } catch (e: FileNotFoundException) {
            ModelStatus.ASSET_MISSING
        } catch (e: Exception) {
            ModelStatus.LOAD_FAILED
        }
        isLoaded = status == ModelStatus.READY
        return status
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
            // A shape mismatch throws on every frame, so record it rather than
            // returning a silent 0f that reads as "wake word not spoken".
            status = ModelStatus.INFERENCE_FAILED
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
        status = ModelStatus.NOT_LOADED
    }

    // The descriptor and stream were never closed, so every load() leaked a file
    // descriptor. The mapping stays valid after both are closed.
    private fun loadModelFile(): MappedByteBuffer =
        context.assets.openFd(modelPath).use { fd ->
            FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fd.startOffset,
                    fd.declaredLength
                )
            }
        }
}
