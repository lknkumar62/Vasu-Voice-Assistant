package com.vasu.assistant.core.stt

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.vasu.assistant.core.wakeword.WakeWordDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * STTManager - Wraps Android SpeechRecognizer for voice input.
 *
 * Capabilities:
 * - Thread safety: All calls guaranteed on the Main Looper.
 * - Anti-collision: Automatically pauses and resumes [WakeWordDetector] to avoid mic lock contention.
 * - Lifecycle recovery: Cleans up and recreates recognizer instances on error/busy states.
 * - Offline speech recognition: Uses on-device speech recognizer on API 33+ when network is absent.
 * - Comprehensive technical diagnostics logged to Logcat with user-friendly error taxonomy.
 */
@Singleton
class STTManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wakeWordDetectorProvider: Provider<WakeWordDetector>
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false

    // State
    private val _state = MutableStateFlow(STTState.IDLE)
    val state: StateFlow<STTState> = _state.asStateFlow()

    // Partial results (streaming) - replay=0 ensures no stale speech results are delivered to new screens
    private val _partialResults = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 1)
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    // Final results - replay=0 ensures one speech input delivers exactly one result event
    private val _results = MutableSharedFlow<RecognitionResult>(replay = 0, extraBufferCapacity = 1)
    val results: SharedFlow<RecognitionResult> = _results.asSharedFlow()

    // RMS level for visualizer
    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    // Errors
    private val _errors = MutableSharedFlow<SttError>(replay = 0, extraBufferCapacity = 1)
    val errors: SharedFlow<SttError> = _errors.asSharedFlow()

    // Config
    private var config = STTConfig()

    /**
     * Initialize the speech recognizer
     */
    fun initialize(sttConfig: STTConfig = STTConfig()) {
        config = sttConfig

        runOnMainThread {
            if (isInitialized && speechRecognizer != null) return@runOnMainThread

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "SpeechRecognizer.isRecognitionAvailable returned false. Check Manifest <queries> and Google Speech Services.")
                _state.value = STTState.ERROR
                _errors.tryEmit(SttError(SttErrorKind.SERVICE_UNAVAILABLE, "No speech recognition service available on this device"))
                return@runOnMainThread
            }

            createInternalRecognizer()
        }
    }

    private fun createInternalRecognizer() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null

            val online = isNetworkAvailable()
            val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !online && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                Log.i(TAG, "Creating on-device SpeechRecognizer (offline mode)")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.i(TAG, "Creating standard SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            recognizer.setRecognitionListener(createListener())
            speechRecognizer = recognizer
            isInitialized = true
            _state.value = STTState.IDLE
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating SpeechRecognizer", e)
            _state.value = STTState.ERROR
            _errors.tryEmit(SttError(SttErrorKind.SERVICE_UNAVAILABLE, "Failed to initialize speech recognizer: ${e.message}"))
        }
    }

    /**
     * Start listening for speech
     */
    fun startListening() {
        runOnMainThread {
            if (!hasRecordAudioPermission()) {
                Log.e(TAG, "[MIC_ERROR] Cannot start listening: RECORD_AUDIO permission not granted")
                _state.value = STTState.ERROR
                _errors.tryEmit(SttError(SttErrorKind.MIC_PERMISSION_DENIED, "Microphone permission denied - grant it in Settings"))
                return@runOnMainThread
            }

            Log.i(TAG, "[MIC_START] Starting microphone listening, lang=${config.language}")

            // Pause WakeWordDetector to prevent microphone contention
            pauseWakeWordMic()

            if (!isInitialized || speechRecognizer == null) {
                createInternalRecognizer()
            }

            val recognizer = speechRecognizer
            if (recognizer == null) {
                Log.e(TAG, "[MIC_ERROR] Speech recognizer not initialized")
                _state.value = STTState.ERROR
                _errors.tryEmit(SttError(SttErrorKind.SERVICE_UNAVAILABLE, "Speech recognizer not initialized"))
                resumeWakeWordMic()
                return@runOnMainThread
            }

            try {
                recognizer.cancel()
                _state.value = STTState.LISTENING

                val intent = createRecognizerIntent()
                Log.i(TAG, "[STT_START] Speech recognition session started with intent")
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "[MIC_ERROR] Exception starting SpeechRecognizer: ${e.message}", e)
                _state.value = STTState.ERROR
                _errors.tryEmit(SttError(SttErrorKind.RECOGNITION_ERROR, "Could not start microphone: ${e.message}"))
                resumeWakeWordMic()
            }
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        runOnMainThread {
            Log.i(TAG, "[MIC_STOP] Stopping microphone listening")
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping speech recognition", e)
            } finally {
                _state.value = STTState.IDLE
                resumeWakeWordMic()
            }
        }
    }

    /**
     * Cancel recognition
     */
    fun cancel() {
        runOnMainThread {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling speech recognition", e)
            } finally {
                _state.value = STTState.IDLE
                resumeWakeWordMic()
            }
        }
    }

    /**
     * Check if recognition is available
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Cleanup resources
     */
    fun destroy() {
        runOnMainThread {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer", e)
            }
            speechRecognizer = null
            isInitialized = false
            _state.value = STTState.IDLE
            resumeWakeWordMic()
        }
    }

    private fun createRecognizerIntent(): Intent {
        val online = isNetworkAvailable()
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.language)
            putExtra(
                RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE,
                config.language
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, config.silenceTimeoutMs)

            if (!online) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = STTState.LISTENING
        }

        override fun onBeginningOfSpeech() {
            _state.value = STTState.LISTENING
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            _state.value = STTState.PROCESSING
        }

        override fun onError(error: Int) {
            val diagnostic = explainErrorCode(error)
            Log.e(TAG, "[STT_ERROR] RecognitionListener.onError: code=$error ($diagnostic), permissionGranted=${hasRecordAudioPermission()}, online=${isNetworkAvailable()}")
            if (error == SpeechRecognizer.ERROR_AUDIO) {
                Log.e(TAG, "[MIC_ERROR] Audio recording error detected")
            }

            val sttError = toSttError(error)
            _state.value = STTState.ERROR
            _errors.tryEmit(sttError)

            resumeWakeWordMic()

            // Reset recognizer instance on critical/busy errors so it never stays permanently broken
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                Log.w(TAG, "Resetting SpeechRecognizer instance due to error code $error")
                speechRecognizer?.destroy()
                speechRecognizer = null
                isInitialized = false
            }

            _state.value = STTState.IDLE
        }

        override fun onResults(results: Bundle?) {
            _state.value = STTState.RESULT_READY
            processResults(results, isFinal = true)
            resumeWakeWordMic()
            _state.value = STTState.IDLE
        }

        override fun onPartialResults(partialResults: Bundle?) {
            processResults(partialResults, isFinal = false)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Additional events
        }
    }

    private fun processResults(results: Bundle?, isFinal: Boolean) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

        if (!matches.isNullOrEmpty()) {
            val primaryText = matches[0]
            val primaryConfidence = confidences?.getOrNull(0) ?: 0f

            Log.i(TAG, "[STT_RESULT] Result received: text=\"$primaryText\", final=$isFinal, confidence=$primaryConfidence")

            val alternatives = matches.drop(1).mapIndexed { index, text ->
                RecognitionResult.AlternativeResult(
                    text = text,
                    confidence = confidences?.getOrNull(index + 1) ?: 0f
                )
            }

            val result = RecognitionResult(
                text = primaryText,
                confidence = primaryConfidence,
                isFinal = isFinal,
                alternatives = alternatives
            )

            if (isFinal) {
                _results.tryEmit(result)
            } else {
                _partialResults.tryEmit(primaryText)
            }
        }
    }

    private fun toSttError(error: Int): SttError {
        val kind = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> SttErrorKind.NO_SPEECH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SttErrorKind.NO_SPEECH
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SttErrorKind.MIC_PERMISSION_DENIED
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SttErrorKind.MIC_BUSY
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SttErrorKind.TIMEOUT
            SpeechRecognizer.ERROR_NETWORK -> SttErrorKind.NETWORK_ERROR
            SpeechRecognizer.ERROR_SERVER -> SttErrorKind.NETWORK_ERROR
            SpeechRecognizer.ERROR_AUDIO -> SttErrorKind.AUDIO_ERROR
            SpeechRecognizer.ERROR_CLIENT -> SttErrorKind.RECOGNITION_ERROR
            else -> mapApi33Error(error)
        }

        val message = when (kind) {
            SttErrorKind.NO_SPEECH -> "No speech detected - try again"
            SttErrorKind.MIC_PERMISSION_DENIED -> "Microphone permission denied - grant it in Settings"
            SttErrorKind.MIC_BUSY -> "Microphone is busy - another app may be using it"
            SttErrorKind.TIMEOUT -> "Speech recognition timed out"
            SttErrorKind.NETWORK_ERROR -> "Speech recognition needs a network connection"
            SttErrorKind.RECOGNITION_ERROR -> "Could not process the audio - try again"
            SttErrorKind.SERVICE_UNAVAILABLE -> "No speech recognition service available on this device"
            SttErrorKind.LANGUAGE_UNAVAILABLE -> "Selected language pack is not installed"
            SttErrorKind.AUDIO_ERROR -> "Microphone capture failed"
            SttErrorKind.RATE_LIMITED -> "Too many recognition requests - wait a moment"
            SttErrorKind.UNKNOWN -> "Speech recognition failed (code $error)"
        }

        return SttError(kind, message, error)
    }

    private fun mapApi33Error(error: Int): SttErrorKind = when (error) {
        10 -> SttErrorKind.RATE_LIMITED           // ERROR_TOO_MANY_REQUESTS
        11 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_SERVER_DISCONNECTED
        12 -> SttErrorKind.LANGUAGE_UNAVAILABLE   // ERROR_LANGUAGE_NOT_SUPPORTED
        13 -> SttErrorKind.LANGUAGE_UNAVAILABLE   // ERROR_LANGUAGE_UNAVAILABLE
        14 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_CANNOT_CHECK_SUPPORT
        15 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_CANNOT_LISTEN_TO_DOWNLOADED_MODEL
        else -> SttErrorKind.UNKNOWN
    }

    private fun explainErrorCode(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        10 -> "ERROR_TOO_MANY_REQUESTS"
        11 -> "ERROR_SERVER_DISCONNECTED"
        12 -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        13 -> "ERROR_LANGUAGE_UNAVAILABLE"
        14 -> "ERROR_CANNOT_CHECK_SUPPORT"
        15 -> "ERROR_CANNOT_LISTEN_TO_DOWNLOADED_MODEL"
        else -> "UNKNOWN_$code"
    }

    private fun pauseWakeWordMic() {
        try {
            wakeWordDetectorProvider.get().pauseForSpeechRecognition()
        } catch (e: Exception) {
            Log.w(TAG, "Failed pausing wake word detector", e)
        }
    }

    private fun resumeWakeWordMic() {
        try {
            wakeWordDetectorProvider.get().resumeAfterSpeechRecognition()
        } catch (e: Exception) {
            Log.w(TAG, "Failed resuming wake word detector", e)
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        private const val TAG = "STTManager"
    }
}
