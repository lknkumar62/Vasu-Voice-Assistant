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
    private val _errors = MutableSharedFlow<String>(replay = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

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
            _errors.tryEmit("Speech recognizer not initialized")
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
            val errorMsg = getErrorMessage(error)
            _state.value = STTState.ERROR
            _errors.tryEmit(errorMsg)

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

    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions - grant microphone access"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected - try again"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition engine busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input - try again"
            else -> "Unknown error (code: $error)"
        }
    }
}
