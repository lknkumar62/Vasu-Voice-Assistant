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

    fun initialize() {
        androidSpeechService.initialize()
        _customVoiceStatus.value = customVoiceEngine.status.value
    }

    fun speak(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            voiceRouter.speak(text)
        }
    }

    fun speakQueued(text: String) {
        speechQueue.enqueue(text)
        processQueue()
    }

    fun stop() {
        speechQueue.clear()
        voiceRouter.stop()
    }

    fun applyProfile(profile: VoiceProfile) {
        androidSpeechService.applyProfile(profile)
    }

    private fun processQueue() {
        if (speechQueue.isProcessing.value) return
        val item = speechQueue.dequeue() ?: return
        speechQueue.setProcessing(true)
        CoroutineScope(Dispatchers.Main).launch {
            voiceRouter.speak(
                text = item.text,
                onDone = {
                    speechQueue.setProcessing(false)
                    processQueue()
                },
                onError = {
                    speechQueue.setProcessing(false)
                    processQueue()
                }
            )
        }
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
