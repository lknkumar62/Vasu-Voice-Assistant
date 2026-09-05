package com.vasu.assistant.core.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * WakeWordDetector - Continuous On-Device Wake-Word Detection Pipeline.
 *
 * Pipeline:
 * 16 kHz Mono PCM AudioRecord
 *        ↓
 * VAD / Energy Gate
 *        ↓
 * Rolling Window (~1.5s / 25,088 samples)
 *        ↓
 * MelSpectrogram (98 frames x 40 mel bands)
 *        ↓
 * TensorFlow Lite (hello_vasu.tflite)
 *        ↓
 * Debounced Wake Word Event ("Hello Vasu")
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = 1600 // 100ms per frame
        private const val ROLLING_WINDOW_SAMPLES = 25088 // 98 frames * 256 hop
        private const val DEBOUNCE_MS = 1800L
        private const val VAD_ENERGY_THRESHOLD = 0.015f // Minimum RMS to invoke inference
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val wakeWordModel = WakeWordModel(context)
    private val melExtractor = MelSpectrogram(
        sampleRate = SAMPLE_RATE,
        fftSize = 512,
        numMelBands = 40
    )

    private val _state = MutableStateFlow(WakeWordState.IDLE)
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _unavailableReason = MutableStateFlow<String?>(null)
    val unavailableReason: StateFlow<String?> = _unavailableReason.asStateFlow()

    private val _detections = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 2)
    val detections: SharedFlow<Unit> = _detections.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var listeningJob: Job? = null

    private val isPausedForStt = AtomicBoolean(false)
    private val isMutedForPlayback = AtomicBoolean(false)
    private var lastDetectionTimestamp = 0L

    // Rolling audio buffer
    private val rollingBuffer = FloatArray(ROLLING_WINDOW_SAMPLES)
    private var rollingBufferFilled = 0

    /**
     * Initialize the TFLite model and check permissions.
     */
    fun initialize() {
        if (_state.value == WakeWordState.LISTENING) return

        val modelStatus = wakeWordModel.load()
        if (modelStatus != ModelStatus.READY) {
            _state.value = WakeWordState.MODEL_NOT_AVAILABLE
            _unavailableReason.value = modelStatus.detail
            Log.w(TAG, "Wake word model unavailable: ${modelStatus.detail}")
            return
        }

        _unavailableReason.value = null
        if (_state.value != WakeWordState.LISTENING) {
            _state.value = WakeWordState.IDLE
        }
        Log.i(TAG, "WakeWordDetector successfully initialized with hello_vasu.tflite")
    }

    /**
     * Start continuous microphone capture and wake-word evaluation.
     */
    @Synchronized
    fun start(): Boolean {
        if (_state.value == WakeWordState.LISTENING) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "Microphone permission not granted"
            Log.e(TAG, "Cannot start WakeWordDetector: RECORD_AUDIO permission missing")
            return false
        }

        if (wakeWordModel.status != ModelStatus.READY) {
            initialize()
            if (wakeWordModel.status != ModelStatus.READY) {
                return false
            }
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "AudioRecord min buffer size invalid: $minBuf"
            return false
        }

        try {
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(minBuf * 2, CHUNK_SAMPLES * 4)
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = WakeWordState.ERROR
                _unavailableReason.value = "AudioRecord failed to initialize"
                return false
            }

            audioRecord?.startRecording()
            _state.value = WakeWordState.LISTENING
            _unavailableReason.value = null
            startListeningLoop()
            Log.i(TAG, "WakeWordDetector started listening continuously for \"Hello Vasu\"")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "AudioRecord start failed: ${e.message}"
            return false
        }
    }

    /**
     * Continuous audio ingestion and inference coroutine loop.
     */
    private fun startListeningLoop() {
        listeningJob?.cancel()
        listeningJob = scope.launch {
            val audioBuffer = ShortArray(CHUNK_SAMPLES)

            while (isActive && _state.value == WakeWordState.LISTENING) {
                // If paused for STT or muted for speaker playback, yield without recording
                if (isPausedForStt.get() || isMutedForPlayback.get()) {
                    delay(50)
                    continue
                }

                val record = audioRecord ?: break
                val readCount = record.read(audioBuffer, 0, CHUNK_SAMPLES)
                if (readCount <= 0) {
                    delay(20)
                    continue
                }

                // Check again to avoid processing buffered audio if state changed during read
                if (isPausedForStt.get() || isMutedForPlayback.get()) {
                    continue
                }

                // Convert 16-bit PCM to normalized Float [-1.0, 1.0] and compute RMS
                var sumSquares = 0.0
                val floatChunk = FloatArray(readCount)
                for (i in 0 until readCount) {
                    val sample = audioBuffer[i] / 32768.0f
                    floatChunk[i] = sample
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / readCount).toFloat()

                // Append chunk into rolling buffer
                appendAudioChunk(floatChunk)

                // VAD Gate: only perform MelSpectrogram & inference if energy indicates speech
                if (rms < VAD_ENERGY_THRESHOLD) {
                    continue
                }

                // Ensure rolling buffer has enough data
                if (rollingBufferFilled < ROLLING_WINDOW_SAMPLES) {
                    continue
                }

                // Check debounce window
                val now = System.currentTimeMillis()
                if (now - lastDetectionTimestamp < DEBOUNCE_MS) {
                    continue
                }

                // Extract Mel Spectrogram and run TFLite inference
                try {
                    val melFeatures = melExtractor.computeMelSpectrogram(rollingBuffer)
                    if (melFeatures.size >= wakeWordModel.inputSize) {
                        // Take the most recent 98 frames
                        val modelInput = Array(wakeWordModel.inputSize) { idx ->
                            melFeatures[melFeatures.size - wakeWordModel.inputSize + idx]
                        }

                        val confidence = wakeWordModel.predict(modelInput)
                        if (confidence >= wakeWordModel.threshold) {
                            lastDetectionTimestamp = now
                            Log.i(TAG, ">>> [WAKE WORD DETECTED] \"Hello Vasu\" heard with confidence $confidence")
                            
                            _state.value = WakeWordState.DETECTED
                            _detections.emit(Unit)

                            // Clear rolling buffer after detection to prevent repeated triggers
                            rollingBufferFilled = 0
                            java.util.Arrays.fill(rollingBuffer, 0f)

                            // Transition back to LISTENING after short indicator interval
                            delay(400)
                            if (_state.value == WakeWordState.DETECTED) {
                                _state.value = WakeWordState.LISTENING
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Wake word inference error: ${e.message}")
                }
            }
        }
    }

    private fun appendAudioChunk(chunk: FloatArray) {
        val size = chunk.size
        if (size >= ROLLING_WINDOW_SAMPLES) {
            System.arraycopy(chunk, size - ROLLING_WINDOW_SAMPLES, rollingBuffer, 0, ROLLING_WINDOW_SAMPLES)
            rollingBufferFilled = ROLLING_WINDOW_SAMPLES
            return
        }

        // Shift left
        System.arraycopy(rollingBuffer, size, rollingBuffer, 0, ROLLING_WINDOW_SAMPLES - size)
        // Copy new samples to the end
        System.arraycopy(chunk, 0, rollingBuffer, ROLLING_WINDOW_SAMPLES - size, size)

        rollingBufferFilled = (rollingBufferFilled + size).coerceAtMost(ROLLING_WINDOW_SAMPLES)
    }

    /**
     * Temporarily pause listening when Android SpeechRecognizer or Gemini Live microphone opens.
     */
    fun pauseForSpeechRecognition() {
        Log.d(TAG, "Pausing wake word for STT")
        isPausedForStt.set(true)
    }

    /**
     * Resume wake word after speech recognition finishes.
     */
    fun resumeAfterSpeechRecognition() {
        Log.d(TAG, "Resuming wake word after STT")
        isPausedForStt.set(false)
    }

    /**
     * Mute microphone detection while VASU assistant is speaking (Echo & Self-trigger protection).
     */
    fun setMutedForPlayback(muted: Boolean) {
        isMutedForPlayback.set(muted)
        if (muted) {
            // Reset buffer so echo doesn't linger
            rollingBufferFilled = 0
        }
    }

    /**
     * Stop and release AudioRecord resources cleanly.
     */
    @Synchronized
    fun stop() {
        listeningJob?.cancel()
        listeningJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        _state.value = WakeWordState.IDLE
        Log.i(TAG, "WakeWordDetector stopped")
    }

    /**
     * Check if detection is available (microphone permission granted).
     */
    fun isAvailable(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if wake word is currently detected.
     */
    fun isDetected(): Boolean = _state.value == WakeWordState.DETECTED

    /**
     * Complete cleanup and release of resources.
     */
    fun destroy() {
        stop()
    }
}
