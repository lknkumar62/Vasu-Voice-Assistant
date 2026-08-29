package com.vasu.assistant.core.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * STTManager - Wraps Android SpeechRecognizer for voice input.
 *
 * Features:
 * - Hindi + English support
 * - Partial result streaming
 * - Error handling
 * - Auto-restart on silence
 * - Lifecycle-aware
 */
@Singleton
class STTManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isInitialized = false

    // State
    private val _state = MutableStateFlow(STTState.IDLE)
    val state: StateFlow<STTState> = _state.asStateFlow()

    // Partial results (streaming)
    private val _partialResults = MutableSharedFlow<String>(replay = 1)
    val partialResults: SharedFlow<String> = _partialResults.asSharedFlow()

    // Final results
    private val _results = MutableSharedFlow<RecognitionResult>(replay = 1)
    val results: SharedFlow<RecognitionResult> = _results.asSharedFlow()

    // Errors
    private val _errors = MutableSharedFlow<SttError>(replay = 1)
    val errors: SharedFlow<SttError> = _errors.asSharedFlow()

    // Config
    private var config = STTConfig()

    /**
     * Initialize the speech recognizer
     */
    fun initialize(sttConfig: STTConfig = STTConfig()) {
        if (isInitialized) return

        config = sttConfig

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = STTState.ERROR
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(createListener())
        }
        isInitialized = true
        _state.value = STTState.IDLE
    }

    /**
     * Start listening for speech
     */
    fun startListening() {
        if (!isInitialized) {
            initialize()
        }

        speechRecognizer?.let { recognizer ->
            _state.value = STTState.LISTENING

            val intent = createRecognizerIntent()
            recognizer.startListening(intent)
        } ?: run {
            _state.value = STTState.ERROR
            _errors.tryEmit(SttError(SttErrorKind.SERVICE_UNAVAILABLE, "Speech recognizer not initialized"))
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
        _state.value = STTState.IDLE
    }

    /**
     * Cancel recognition
     */
    fun cancel() {
        speechRecognizer?.cancel()
        _state.value = STTState.IDLE
    }

    /**
     * Check if recognition is available
     */
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Cleanup resources
     */
    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        isInitialized = false
        _state.value = STTState.IDLE
    }

    // Private methods

    private fun createRecognizerIntent(): Intent {
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
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, config.silenceTimeoutMs)
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
            // RMS level for waveform visualization
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            // Audio buffer received
        }

        override fun onEndOfSpeech() {
            _state.value = STTState.PROCESSING
        }

        override fun onError(error: Int) {
            val sttError = toSttError(error)
            _state.value = STTState.ERROR
            _errors.tryEmit(sttError)

            // Auto-restart on certain errors (silence, no match)
            if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ) {
                _state.value = STTState.IDLE
            }
        }

        override fun onResults(results: Bundle?) {
            _state.value = STTState.RESULT_READY
            processResults(results, isFinal = true)
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
            // ERROR_CLIENT is the framework's catch-all. It is overwhelmingly caused
            // by the recogniser being driven off the main thread or being reused
            // after destroy, not by anything the user did, so it maps to a
            // recognition fault rather than the meaningless "Client error".
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

    /**
     * Codes added in API 31/33. Referenced numerically because the app compiles
     * against a range of SDKs and the constants are not present on all of them.
     */
    private fun mapApi33Error(error: Int): SttErrorKind = when (error) {
        10 -> SttErrorKind.RATE_LIMITED           // ERROR_TOO_MANY_REQUESTS
        11 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_SERVER_DISCONNECTED
        12 -> SttErrorKind.LANGUAGE_UNAVAILABLE   // ERROR_LANGUAGE_NOT_SUPPORTED
        13 -> SttErrorKind.LANGUAGE_UNAVAILABLE   // ERROR_LANGUAGE_UNAVAILABLE
        14 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_CANNOT_CHECK_SUPPORT
        15 -> SttErrorKind.SERVICE_UNAVAILABLE    // ERROR_CANNOT_LISTEN_TO_DOWNLOADED_MODEL
        else -> SttErrorKind.UNKNOWN
    }
}
