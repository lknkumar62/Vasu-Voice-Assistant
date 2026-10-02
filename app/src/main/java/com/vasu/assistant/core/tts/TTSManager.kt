package com.vasu.assistant.core.tts

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.vasu.assistant.core.voice.VoiceStateManager
import com.vasu.assistant.core.voice.GeminiVoiceState

/**
 * TTSManager - single front door for all assistant speech.
 *
 * EVENT-DRIVEN CONTRACT:
 * - TTS is triggered only by explicit call sites (Chat sendMessage,
 *   Voice processVoiceCommand, settings preview) — never by recomposition,
 *   scroll, tab switch, history load or database observers.
 * - [speakQueued] plays items strictly FIFO; each item completes via its
 *   engine callback (TTS_COMPLETED) before the next starts. No fixed delays.
 */
@Singleton
class TTSManager @Inject constructor(
    private val voiceRouter: VoiceRouter,
    private val speechQueue: SpeechQueue,
    private val androidSpeechService: AndroidHindiSpeechService,
    private val customVoiceEngine: CustomVoiceEngine,
    private val voiceStateManager: VoiceStateManager
) {
    val state: StateFlow<TTSState> get() = androidSpeechService.state
    val activeVoiceSource: StateFlow<ActiveVoiceSource> get() = voiceRouter.currentSource
    val availableLanguages: StateFlow<List<String>> get() = androidSpeechService.availableVoices
    val voiceStatus: StateFlow<VoiceStatus> get() = androidSpeechService.voiceStatus

    private val _customVoiceStatus = MutableStateFlow(customVoiceEngine.status.value)
    val customVoiceStatus: StateFlow<VoiceModelStatus> = _customVoiceStatus.asStateFlow()

    /** One-shot completion hooks per queued ttsId (command-queue draining). */
    private val completionCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    /** Tracks the last response ID that was actually spoken to prevent duplicates. */
    private var lastSpokenResponseId: String? = null

    fun initialize() {
        try {
            androidSpeechService.initialize()
            // Pre-warm Android fallback TTS so voice is ready even when Gemini fails
            try {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        androidSpeechService.initialize()
                    } catch (e: Exception) {
                        Log.e(TAG, "TTS fallback warm failed", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS fallback warm launch failed", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS initialize failed — degrading to silent", e)
            com.vasu.assistant.core.logging.ErrorLog.log("VOICE", "TTS init failed: ${e.message}", e)
        }
        try {
            _customVoiceStatus.value = customVoiceEngine.status.value
        } catch (e: Exception) {
            Log.e(TAG, "customVoiceStatus read failed", e)
        }
    }

    fun speak(text: String) {
        Log.w(TAG, "Direct speak() bypasses the FIFO queue (settings preview only)")
        CoroutineScope(Dispatchers.Main).launch {
            voiceRouter.speak(text)
        }
    }

    fun speakQueued(text: String, responseId: String? = null) {
        speakQueued(text, responseId, onComplete = null)
    }

    /**
     * Enqueues each sentence as its own queued item so the first sentence
     * starts synthesizing/playing immediately instead of handing the entire
     * response to the engine as one request. [onComplete] fires exactly once,
     * after the LAST sentence finishes (or immediately when the list is
     * empty). Per-item responseId is null so SpeechQueue's consecutive
     * responseId de-dupe never collapses distinct sentences.
     */
    fun speakQueuedBatch(sentences: List<String>, onComplete: (() -> Unit)? = null) {
        val clean = sentences.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) {
            onComplete?.invoke()
            return
        }
        clean.forEachIndexed { index, sentence ->
            speakQueued(
                sentence,
                responseId = null,
                onComplete = if (index == clean.lastIndex) onComplete else null
            )
        }
    }

    /**
     * Enqueue one complete assistant response for serial playback.
     * [onComplete] fires exactly once when this item's audio completes
     * (or fails), letting owners drain command queues FIFO without overlap.
     * A consecutive-duplicate enqueue is merged into the already-queued
     * item: its hook is intentionally NOT fired, because the original item
     * will complete (or fail) and fire its own hook later.
     */
    fun speakQueued(text: String, responseId: String? = null, onComplete: (() -> Unit)?) {
        val item = speechQueue.enqueue(text, responseId = responseId)
        if (item == null) {
            // De-duplicated onto the still-queued identical item: wait for it.
            return
        }
        if (onComplete != null) {
            completionCallbacks[item.ttsId] = onComplete
        }
        processQueue()
    }

    fun stop() {
        speechQueue.clear()
        // Cancelled items never played: drop their hooks without firing so
        // owners do not advance queues on turns that never spoke.
        completionCallbacks.clear()
        voiceRouter.stop()
    }

    fun applyProfile(profile: VoiceProfile) {
        androidSpeechService.applyProfile(profile)
    }

    private fun processQueue() {
        if (speechQueue.isProcessing.value) return
        val item = speechQueue.dequeue() ?: return

        // Absolute Duplicate Protection: skip if this response was already spoken
        if (item.responseId != null && item.responseId == lastSpokenResponseId) {
            Log.w(TAG, "Skipping duplicate responseId: ${item.responseId}")
            processQueue()
            return
        }

        speechQueue.setProcessing(true)
        Log.d(TAG, "TTS_START ttsId=${item.ttsId} queueSize=${speechQueue.queueSize.value}")
        CoroutineScope(Dispatchers.Main).launch {
            voiceStateManager.transitionTo(GeminiVoiceState.SPEAKING)
            voiceRouter.speak(
                text = item.text,
                onDone = {
                    Log.i(TAG, "TTS_COMPLETED ttsId=${item.ttsId}")
                    lastSpokenResponseId = item.responseId
                    speechQueue.setProcessing(false)
                    completionCallbacks.remove(item.ttsId)?.invoke()
                    CoroutineScope(Dispatchers.Main).launch {
                        voiceStateManager.transitionTo(GeminiVoiceState.BACKGROUND_LISTENING)
                    }
                    processQueue()
                },
                onError = { err ->
                    Log.w(TAG, "TTS item failed ttsId=${item.ttsId} error=$err")
                    lastSpokenResponseId = item.responseId
                    speechQueue.setProcessing(false)
                    completionCallbacks.remove(item.ttsId)?.invoke()
                    CoroutineScope(Dispatchers.Main).launch {
                        voiceStateManager.transitionTo(GeminiVoiceState.BACKGROUND_LISTENING)
                    }
                    processQueue()
                }
            )
        }
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
