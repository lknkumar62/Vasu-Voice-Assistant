package com.vasu.assistant.ui.diagnostics

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.VoiceModelStatus
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DiagnosticsUiState(
    val items: List<DiagnosticItem> = emptyList()
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: SecureKeyStore,
    private val wakeWordDetector: WakeWordDetector,
    private val ttsManager: TTSManager,
    private val geminiLiveVoiceService: GeminiLiveVoiceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        val items = mutableListOf<DiagnosticItem>()

        // AI Provider
        items.add(DiagnosticItem("AI Provider",
            "Gemini: ${if (keyStore.hasGeminiKey()) "Configured" else "Not configured"}",
            if (keyStore.hasGeminiKey()) DiagnosticStatus.OK else DiagnosticStatus.WARNING))

        items.add(DiagnosticItem("Gemini Model", keyStore.geminiModel, DiagnosticStatus.INFO))

        // Speech Recognition
        val sttAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        items.add(DiagnosticItem("Speech Recognition",
            if (sttAvailable) "Available" else "Not available",
            if (sttAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR))

        val onDeviceStt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) else false
        items.add(DiagnosticItem("On-Device STT",
            if (onDeviceStt) "Available" else "Not available",
            if (onDeviceStt) DiagnosticStatus.OK else DiagnosticStatus.INFO))

        // Wake Word
        val wakeState = wakeWordDetector.state.value
        items.add(DiagnosticItem("Wake Word Model",
            when (wakeState) {
                WakeWordState.IDLE -> "Loaded (idle)"
                WakeWordState.LISTENING -> "Active"
                WakeWordState.DETECTED -> "Detected"
                WakeWordState.MODEL_NOT_AVAILABLE -> "NOT FOUND"
                WakeWordState.ERROR -> "ERROR"
            },
            when (wakeState) {
                WakeWordState.IDLE, WakeWordState.LISTENING -> DiagnosticStatus.OK
                WakeWordState.DETECTED -> DiagnosticStatus.INFO
                WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> DiagnosticStatus.ERROR
            }))

        wakeWordDetector.unavailableReason.value?.let {
            items.add(DiagnosticItem("Wake Word Reason", it, DiagnosticStatus.ERROR))
        }

        // Service
        val svcState = VasuForegroundService.serviceState.value
        items.add(DiagnosticItem("Foreground Service", svcState.name,
            if (svcState.name == "LISTENING" || svcState.name == "ACTIVE") DiagnosticStatus.OK else DiagnosticStatus.INFO))

        // Voice
        val customStatus = ttsManager.customVoiceStatus.value
        items.add(DiagnosticItem("Custom Voice",
            when (customStatus) {
                VoiceModelStatus.ACTIVE_CUSTOM_MODEL -> "Neural model active"
                VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> "Custom samples loaded"
                VoiceModelStatus.FALLBACK_SYSTEM_TTS -> "System TTS fallback"
                VoiceModelStatus.ERROR -> "Error loading"
            },
            when (customStatus) {
                VoiceModelStatus.ACTIVE_CUSTOM_MODEL, VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> DiagnosticStatus.OK
                VoiceModelStatus.FALLBACK_SYSTEM_TTS -> DiagnosticStatus.INFO
                VoiceModelStatus.ERROR -> DiagnosticStatus.ERROR
            }))

        items.add(DiagnosticItem("TTS Engine", ttsManager.state.value.name, DiagnosticStatus.INFO))
        items.add(DiagnosticItem("Live Voice", geminiLiveVoiceService.voiceState.value.name, DiagnosticStatus.INFO))

        // Device
        items.add(DiagnosticItem("Device", "${Build.MANUFACTURER} ${Build.MODEL}", DiagnosticStatus.INFO))
        items.add(DiagnosticItem("Android", "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})", DiagnosticStatus.INFO))

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        items.add(DiagnosticItem("Audio Mode",
            when (audioManager?.mode) {
                AudioManager.MODE_NORMAL -> "Normal"
                AudioManager.MODE_IN_CALL -> "In Call"
                AudioManager.MODE_IN_COMMUNICATION -> "In Communication"
                AudioManager.MODE_RINGTONE -> "Ringtone"
                else -> "Unknown"
            }, DiagnosticStatus.INFO))

        _uiState.value = DiagnosticsUiState(items = items)
    }
}
