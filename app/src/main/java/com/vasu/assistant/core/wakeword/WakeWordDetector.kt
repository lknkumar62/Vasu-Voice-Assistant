package com.vasu.assistant.core.wakeword

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.vasu.assistant.core.audio.AudioSessionManager
import com.vasu.assistant.core.security.RoleManager
import com.vasu.assistant.core.security.SpeakerEmbeddingGenerator
import com.vasu.assistant.core.security.SpeakerVerifier
import com.vasu.assistant.core.security.VerificationResult
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
import com.vasu.assistant.core.voice.GeminiVoiceState
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
 * Rolling Window (~1.58s / 25,344 samples = 98 frames)
 *        ↓
 * MelSpectrogram (98 frames x 40 mel bands)
 *        ↓
 * TensorFlow Lite (hello_vasu.tflite)
 *        ↓
 * Debounced Wake Word Event ("Hello Vasu")
 *
 * AudioSessionManager integration:
 * - On detection, immediately releases mic so STT/Gemini can acquire it
 * - Uses pause/resume for external control, plus mic release via AudioSessionManager
 */
@Singleton
class WakeWordDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioSessionManager: AudioSessionManager,
    private val voiceStateManager: com.vasu.assistant.core.voice.VoiceStateManager,
    private val voiceGuardian: com.vasu.assistant.core.guardian.VoiceGuardian,
    private val roleManager: RoleManager,
    private val speakerVerifier: SpeakerVerifier
) {
    companion object {
        private const val TAG = "WakeWordDetector"
        const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SAMPLES = 1600 // 100ms per frame
        private const val FFT_SIZE = 512
        private const val HOP_SIZE = FFT_SIZE / 2

        /**
         * Window must yield exactly [WakeWordModel.INPUT_SIZE] mel frames because
         * numFrames = (window - FFT_SIZE) / HOP_SIZE + 1. The previous 25,088
         * window produced only 97 frames, so the detector gate
         * (`melFeatures.size >= inputSize`) silently rejected every inference and
         * "Hello Vasu" could never fire. Derived, not hardcoded, so the two
         * constants cannot drift apart again. Internal so the frame-count
         * contract is pinned by a unit test.
         */
        internal const val ROLLING_WINDOW_SAMPLES =
            (WakeWordModel.INPUT_SIZE - 1) * HOP_SIZE + FFT_SIZE
        private const val DEBOUNCE_MS = 1800L
        private const val VAD_ENERGY_THRESHOLD = 0.015f
        private const val REARM_RETRY_DELAY_MS = 500L
        private const val REARM_MAX_ATTEMPTS = 12
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val wakeWordModel = WakeWordModel(context)
    private val melExtractor = MelSpectrogram(
        sampleRate = SAMPLE_RATE,
        fftSize = FFT_SIZE,
        numMelBands = WakeWordModel.NUM_MEL_BANDS
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

    // True while the user wants continuous wake-word listening. A detection
    // releases the mic (releaseMicForCommand) but the listener stays "wanted"
    // until stop(), so resumeAfterSpeechRecognition() can re-acquire the mic and
    // restart capture - otherwise the detector stayed deaf after the first use.
    private val shouldBeListening = AtomicBoolean(false)
    private var rearmJob: Job? = null
    private var lastDetectionTimestamp = 0L

    // Rolling audio buffer
    private val rollingBuffer = FloatArray(ROLLING_WINDOW_SAMPLES)
    private var rollingBufferFilled = 0

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
     * Requests mic from AudioSessionManager before creating AudioRecord.
     */
    @Synchronized
    fun start(): Boolean = startInternal(assertIntent = true, requireMicFree = false)

    /**
     * @param assertIntent user/service-initiated starts re-assert the listening
     *   intent; the re-arm path passes false and aborts if stop() cleared the
     *   intent while the re-arm was in flight.
     * @param requireMicFree never force-steal mic ownership (re-arm path).
     */
    @Synchronized
    private fun startInternal(assertIntent: Boolean, requireMicFree: Boolean): Boolean {
        if (_state.value == WakeWordState.LISTENING) return true

        if (assertIntent) {
            // Intent, not capture success: a start that fails because the mic is
            // briefly busy must still be retried by the next resume.
            shouldBeListening.set(true)
        } else if (!shouldBeListening.get()) {
            // stop() ran while this re-arm was in flight - do not resurrect capture.
            return false
        }

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

        // Request mic from AudioSessionManager. The re-arm path asks atomically
        // for a free mic instead of stealing ownership from another consumer.
        if (requireMicFree) {
            if (!audioSessionManager.requestMicIfFree(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)) {
                Log.d(TAG, "Re-arm waiting: mic owned by ${audioSessionManager.currentOwner.value.label}")
                return false
            }
        } else if (!audioSessionManager.requestMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)) {
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "Microphone is occupied by another component"
            Log.w(TAG, "Cannot start WakeWordDetector: mic not available")
            return false
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuf <= 0) {
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "AudioRecord min buffer size invalid: $minBuf"
            audioSessionManager.releaseMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)
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
                audioSessionManager.releaseMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)
                return false
            }

            audioRecord?.startRecording()
            _state.value = WakeWordState.LISTENING
            _unavailableReason.value = null
            audioSessionManager.transitionState(AudioSessionManager.SessionState.WAKE_LISTENING)
            startListeningLoop()
            Log.i(TAG, "WakeWordDetector started listening continuously for \"Hello Vasu\"")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting AudioRecord", e)
            _state.value = WakeWordState.ERROR
            _unavailableReason.value = "AudioRecord start failed: ${e.message}"
            audioSessionManager.releaseMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)
            return false
        }
    }

    private fun startListeningLoop() {
        listeningJob?.cancel()
        listeningJob = scope.launch {
            val audioBuffer = ShortArray(CHUNK_SAMPLES)

            while (isActive && _state.value == WakeWordState.LISTENING) {
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

                if (isPausedForStt.get() || isMutedForPlayback.get()) {
                    continue
                }

                var sumSquares = 0.0
                val floatChunk = FloatArray(readCount)
                for (i in 0 until readCount) {
                    val sample = audioBuffer[i] / 32768.0f
                    floatChunk[i] = sample
                    sumSquares += sample * sample
                }
                val rms = sqrt(sumSquares / readCount).toFloat()

                appendAudioChunk(floatChunk)

                if (rms < VAD_ENERGY_THRESHOLD) {
                    continue
                }

                if (rollingBufferFilled < ROLLING_WINDOW_SAMPLES) {
                    continue
                }

                val now = System.currentTimeMillis()
                if (now - lastDetectionTimestamp < DEBOUNCE_MS) {
                    continue
                }

                try {
                    val melFeatures = melExtractor.computeMelSpectrogram(rollingBuffer)
                    if (melFeatures.size >= wakeWordModel.inputSize) {
                        val modelInput = Array(wakeWordModel.inputSize) { idx ->
                            melFeatures[melFeatures.size - wakeWordModel.inputSize + idx]
                        }

                        val confidence = wakeWordModel.predict(modelInput)
                        if (confidence >= wakeWordModel.threshold) {
                            lastDetectionTimestamp = now
                            Log.i(TAG, ">>> [WAKE WORD DETECTED] \"Hello Vasu\" heard with confidence $confidence")

                            // 1. Transition to WAKE_DETECTED
                            voiceStateManager.transitionTo(GeminiVoiceState.WAKE_DETECTED)
                            _state.value = WakeWordState.DETECTED

                            // 2. Verify Owner (bundled cohort) + enrolled Voice Guardian voices
                            val isOwner = voiceGuardian.verifyOwner(rollingBuffer)
                            var guardianPassed = true
                            if (isOwner && roleManager.listVoices().isNotEmpty()) {
                                val embedding = SpeakerEmbeddingGenerator.generate(rollingBuffer)
                                when (val verdict = speakerVerifier.verify(embedding, roleManager.listVoices())) {
                                    is VerificationResult.Verified -> {
                                        roleManager.setCurrentSpeaker(verdict.speaker)
                                        roleManager.recordVerification(verdict.speaker.id)
                                        Log.i(
                                            TAG,
                                            ">>> [VOICE GUARDIAN] Enrolled speaker verified: " +
                                                "${verdict.speaker.name} (${verdict.speaker.role.displayName})"
                                        )
                                    }
                                    else -> {
                                        if (roleManager.guardianEnabled.value) {
                                            guardianPassed = false
                                            Log.w(TAG, ">>> [VOICE GUARDIAN] Enrolled voice not verified. Ignoring wake word.")
                                        }
                                    }
                                }
                            }

                            if (isOwner && guardianPassed) {
                                Log.i(TAG, ">>> [VOICE GUARDIAN] Owner verified. Proceeding to command listening.")
                                
                                // 3. Transition to COMMAND_LISTENING and emit event
                                voiceStateManager.transitionTo(GeminiVoiceState.COMMAND_LISTENING)
                                _detections.emit(Unit)

                                rollingBufferFilled = 0
                                java.util.Arrays.fill(rollingBuffer, 0f)

                                // CRITICAL: Release mic immediately so STT/Gemini can acquire it.
                                // Capture is fully stopped here, so the state must NOT stay
                                // LISTENING: that made start() short-circuit and left the
                                // detector deaf forever. resumeAfterSpeechRecognition()
                                // re-arms instead.
                                releaseMicForCommand()
                                _state.value = WakeWordState.IDLE
                            } else {
                                Log.w(TAG, ">>> [VOICE GUARDIAN] Owner verification failed. Ignoring wake word.")
                                voiceStateManager.transitionTo(GeminiVoiceState.BACKGROUND_LISTENING)
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

    /**
     * Release mic after wake word detection so STT/Gemini can acquire it.
     * Stops the AudioRecord, releases it, then notifies AudioSessionManager.
     * Synchronized because the listening coroutine (Dispatchers.Default) calls
     * this while start()/stop() run on the main thread.
     */
    @Synchronized
    private fun releaseMicForCommand() {
        try {
            listeningJob?.cancel()
            listeningJob = null

            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
            audioRecord = null

            audioSessionManager.releaseMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)
            Log.d(TAG, "Mic released for command processing")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing mic for command: ${e.message}")
        }
    }

    private fun appendAudioChunk(chunk: FloatArray) {
        val size = chunk.size
        if (size >= ROLLING_WINDOW_SAMPLES) {
            System.arraycopy(chunk, size - ROLLING_WINDOW_SAMPLES, rollingBuffer, 0, ROLLING_WINDOW_SAMPLES)
            rollingBufferFilled = ROLLING_WINDOW_SAMPLES
            return
        }

        System.arraycopy(rollingBuffer, size, rollingBuffer, 0, ROLLING_WINDOW_SAMPLES - size)
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
     * Resume wake word after speech recognition (or a Gemini Live session) finishes.
     *
     * A detection releases the mic ([releaseMicForCommand]), so capture is not
     * running anymore and just clearing the pause flag left the detector deaf.
     * Re-arm here - but only once nobody else owns the mic: start() would
     * force-release an active Gemini Live session (AudioSessionManager grants by
     * stealing ownership) and break its capture mid-conversation. The re-arm
     * therefore polls briefly for the mic to free up and gives up silently.
     */
    fun resumeAfterSpeechRecognition() {
        Log.d(TAG, "Resuming wake word after STT")
        isPausedForStt.set(false)
        if (shouldBeListening.get() && _state.value != WakeWordState.LISTENING) {
            rearmWhenMicFree()
        }
    }

    private fun rearmWhenMicFree() {
        rearmJob?.cancel()
        rearmJob = scope.launch {
            repeat(REARM_MAX_ATTEMPTS) {
                if (!shouldBeListening.get() || _state.value == WakeWordState.LISTENING) {
                    return@launch
                }
                // Non-mic failures would only churn state (permission denied,
                // model unreadable), so stop polling for them.
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(TAG, "Re-arm skipped: RECORD_AUDIO not granted")
                    return@launch
                }
                if (wakeWordModel.status != ModelStatus.READY) {
                    Log.d(TAG, "Re-arm skipped: wake word model not ready")
                    return@launch
                }
                if (startInternal(assertIntent = false, requireMicFree = true)) {
                    return@launch
                }
                delay(REARM_RETRY_DELAY_MS)
            }
            Log.d(TAG, "Wake word not re-armed this time; mic stayed in use")
        }
    }

    /**
     * Mute microphone detection while VASU assistant is speaking (Echo & Self-trigger protection).
     */
    fun setMutedForPlayback(muted: Boolean) {
        isMutedForPlayback.set(muted)
        if (muted) {
            rollingBufferFilled = 0
        }
    }

    /**
     * Stop and release AudioRecord resources cleanly.
     */
    @Synchronized
    fun stop() {
        shouldBeListening.set(false)
        rearmJob?.cancel()
        rearmJob = null
        listeningJob?.cancel()
        listeningJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        audioSessionManager.releaseMic(AudioSessionManager.MicOwner.WAKE_WORD_DETECTOR)
        _state.value = WakeWordState.IDLE
        Log.i(TAG, "WakeWordDetector stopped")
    }
}
