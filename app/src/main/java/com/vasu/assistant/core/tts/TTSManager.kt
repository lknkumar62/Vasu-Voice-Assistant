package com.vasu.assistant.core.tts

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

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
    private val customVoiceEngine: CustomVoiceEngine
) {
    val state: StateFlow<TTSState> get() = androidSpeechService.state
    val activeVoiceSource: StateFlow<ActiveVoiceSource> get() = voiceRouter.currentSource
    val availableLanguages: StateFlow<List<Locale>> get() = androidSpeechService.availableVoices
    val voiceStatus: StateFlow<VoiceStatus> get() = androidSpeechService.voiceStatus

    private val _customVoiceStatus = MutableStateFlow(customVoiceEngine.status.value)
    val customVoiceStatus: StateFlow<VoiceModelStatus> = _customVoiceStatus.asStateFlow()

    /** One-shot completion hooks per queued ttsId (command-queue draining). */
    private val completionCallbacks = java.util.concurrent.ConcurrentHashMap<String, () -> Unit>()

    fun initialize() {
        androidSpeechService.initialize()
        // Pre-warm Android fallback TTS so voice is ready even when Gemini fails
        try {
            // Access via VoiceRouter's lazy dependency — initialize eagerly
            // We use a coroutine to avoid blocking
            CoroutineScope(Dispatchers.Main).launch {
                // Force init of fallback engine via reflection-free call
                // VoiceRouter will lazily init it anyway, but we warm it now
                androidSpeechService.initialize()
            }
        } catch (_: Exception) {}
        _customVoiceStatus.value = customVoiceEngine.status.value
    }

    fun speak(text: String) {
        Log.w(TAG, "Direct speak() bypasses the FIFO queue (settings preview only)")
        CoroutineScope(Dispatchers.Main).launch {
            voiceRouter.speak(text)
        }
    }

    fun speakQueued(text: String) {
        speakQueued(text, onComplete = null)
    }

    /**
     * Enqueue one complete assistant response for serial playback.
     * [onComplete] fires exactly once when this item's audio completes
     * (or fails), letting owners drain command queues FIFO without overlap.
     * A consecutive-duplicate enqueue is merged into the already-queued
     * item: its hook is intentionally NOT fired, because the original item
     * will complete (or fail) and fire its own hook later.
     */
    fun speakQueued(text: String, onComplete: (() -> Unit)?) {
        val item = speechQueue.enqueue(text)
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
        speechQueue.setProcessing(true)
        Log.d(TAG, "TTS_START ttsId=${item.ttsId} queueSize=${speechQueue.queueSize.value}")
        CoroutineScope(Dispatchers.Main).launch {
            voiceRouter.speak(
                text = item.text,
                onDone = {
                    Log.i(TAG, "TTS_COMPLETED ttsId=${item.ttsId}")
                    speechQueue.setProcessing(false)
                    completionCallbacks.remove(item.ttsId)?.invoke()
                    processQueue()
                },
                onError = { err ->
                    Log.w(TAG, "TTS item failed ttsId=${item.ttsId} error=$err")
                    speechQueue.setProcessing(false)
                    completionCallbacks.remove(item.ttsId)?.invoke()
                    processQueue()
                }
            )
        }
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
