package com.vasu.assistant.core.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WakeWordDetector - Detects "Hello Vasu" wake word.
 *
 * Features:
 * - Continuous background listening
 * - TensorFlow Lite-based detection
 * - Low power consumption
 * - False positive reduction
 * - Auto-restart after timeout
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var model: WakeWordModel? = null
    private var melSpectrogram: MelSpectrogram? = null
    private var recordingThread: HandlerThread? = null
    private var recordingHandler: Handler? = null

    private var isRunning = false
    private var isModelLoaded = false

    // State
    private val _state = MutableStateFlow(WakeWordState.IDLE)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    // Detection events
    private val _detections = MutableSharedFlow<String>(replay = 1)
    val detections: SharedFlow<String> = _detections.asSharedFlow()

    // Config
    private var config = WakeWordConfig()

    // Audio configuration
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    // Detection buffer
    private val detectionBuffer = FloatArray(16000) // 1 second buffer
    private var bufferIndex = 0
    private val detectionInterval = 16000 // Process every 1 second

    // Silence detection
    private var silenceCounter = 0
    private val silenceThreshold = 500 // 500ms of silence
    private val speechThreshold = 500 // Minimum speech duration

    // Cooldown tracking
    private var lastDetectionTime = 0L

    /**
     * Initialize the wake word detector
     */
    fun initialize(wakeWordConfig: WakeWordConfig = WakeWordConfig()) {
        if (isRunning) return

        config = wakeWordConfig

        // Check permission
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = WakeWordState.ERROR
            return
        }

        // Initialize audio record
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            // Initialize mel spectrogram
            melSpectrogram = MelSpectrogram(
                sampleRate = sampleRate,
                fftSize = 512,
                numMelBands = 40
            )

            // Load TFLite model
            model = WakeWordModel(context)
            isModelLoaded = model?.load() ?: false

            if (!isModelLoaded) {
                _state.value = WakeWordState.MODEL_NOT_AVAILABLE
                return
            }

            _state.value = WakeWordState.IDLE
        } catch (e: Exception) {
            _state.value = WakeWordState.ERROR
        }
    }

    /**
     * Start wake word detection
     */
    fun start() {
        if (isRunning) return
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            initialize()
        }

        // Bail out if initialization failed (permission denied, model not available, etc.)
        if (_state.value == WakeWordState.ERROR || _state.value == WakeWordState.MODEL_NOT_AVAILABLE) {
            return
        }

        isRunning = true
        _state.value = WakeWordState.LISTENING

        // Start recording thread
        recordingThread = HandlerThread("WakeWordRecording").apply {
            start()
        }
        recordingHandler = Handler(recordingThread!!.looper)

        // Start audio recording
        audioRecord?.startRecording()

        // Start detection loop
        recordingHandler?.post(::detectionLoop)
    }

    /**
     * Stop wake word detection
     */
    fun stop() {
        isRunning = false
        _state.value = WakeWordState.IDLE

        recordingHandler?.removeCallbacksAndMessages(null)
        recordingThread?.quitSafely()
        recordingThread = null
        recordingHandler = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Check if detection is available
     */
    fun isAvailable(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Cleanup resources
     */
    fun destroy() {
        stop()
        model?.close()
        audioRecord?.release()
        audioRecord = null
        isModelLoaded = false
    }

    /**
     * Check if wake word is currently detected
     */
    fun isDetected(): Boolean {
        return _state.value == WakeWordState.DETECTED
    }

    private fun detectionLoop() {
        if (!isRunning) return

        val audioBuffer = ShortArray(bufferSize / 2)

        while (isRunning) {
            try {
                // Read audio data
                val readSize = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                if (readSize > 0) {
                    // Convert to float
                    val floatBuffer = FloatArray(readSize) { i ->
                        audioBuffer[i].toFloat() / Short.MAX_VALUE
                    }

                    // Check for speech
                    val hasSpeech = checkForSpeech(floatBuffer)

                    if (hasSpeech) {
                        // Add to detection buffer
                        for (sample in floatBuffer) {
                            if (bufferIndex < detectionBuffer.size) {
                                detectionBuffer[bufferIndex++] = sample
                            }
                        }

                        // Process when buffer is full
                        if (bufferIndex >= detectionBuffer.size) {
                            processBuffer()
                            bufferIndex = 0
                        }

                        silenceCounter = 0
                    } else {
                        silenceCounter++

                        // If silence for too long, reset buffer
                        if (silenceCounter > silenceThreshold) {
                            bufferIndex = 0
                            silenceCounter = 0
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    _state.value = WakeWordState.ERROR
                }
            }
        }
    }

    private fun checkForSpeech(buffer: FloatArray): Boolean {
        // Simple energy-based VAD
        var energy = 0f
        for (sample in buffer) {
            energy += sample * sample
        }
        energy /= buffer.size

        return energy > 0.01f // Threshold for speech
    }

    private fun processBuffer() {
        if (!isModelLoaded || model == null || melSpectrogram == null) return

        // Skip processing during cooldown period
        val now = System.currentTimeMillis()
        if (now - lastDetectionTime < config.cooldownMs) return

        // Extract mel spectrogram features
        val featuresRaw = melSpectrogram?.extractForWakeWord(detectionBuffer)
        val features = featuresRaw?.let { raw ->
            Array(98) { i -> FloatArray(40) { j -> raw.getOrElse(i * 40 + j) { 0f } } }
        }

        if (features != null) {
            // Run inference
            val detected = model?.detect(features) ?: false

            if (detected) {
                lastDetectionTime = now
                _state.value = WakeWordState.DETECTED
                _detections.tryEmit("Hello Vasu")

                // Reset to listening after detection (non-blocking)
                _state.value = WakeWordState.LISTENING
            }
        }
    }
}

/**
 * Wake word detection state
 */
enum class WakeWordState {
    IDLE,
    LISTENING,
    DETECTED,
    MODEL_NOT_AVAILABLE,
    ERROR
}

/**
 * Wake word configuration
 */
data class WakeWordConfig(
    val wakePhrase: String = "Hello Vasu",
    val threshold: Float = 0.7f,
    val cooldownMs: Long = 3000,        // 3 seconds cooldown after detection
    val maxSilenceMs: Long = 5000,      // 5 seconds silence = stop listening
    val enableAutoRestart: Boolean = true
)
