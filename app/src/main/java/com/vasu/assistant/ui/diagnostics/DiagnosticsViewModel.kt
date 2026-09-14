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
        items.add(DiagnosticItem(
            name = "AI Provider",
            status = if (keyStore.hasGeminiKey()) DiagnosticStatus.OK else DiagnosticStatus.WARNING,
            details = "Gemini: ${if (keyStore.hasGeminiKey()) "Configured" else "Not configured"}"
        ))

        items.add(DiagnosticItem(name = "Gemini Model", status = DiagnosticStatus.INFO, details = keyStore.geminiModel))

        // Speech Recognition
        val sttAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        items.add(DiagnosticItem(
            name = "Speech Recognition",
            status = if (sttAvailable) DiagnosticStatus.OK else DiagnosticStatus.ERROR,
            details = if (sttAvailable) "Available" else "Not available"
        ))

        val onDeviceStt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) else false
        items.add(DiagnosticItem(
            name = "On-Device STT",
            status = if (onDeviceStt) DiagnosticStatus.OK else DiagnosticStatus.INFO,
            details = if (onDeviceStt) "Available" else "Not available"
        ))

        // Wake Word
        val wakeState = wakeWordDetector.state.value
        items.add(DiagnosticItem(
            name = "Wake Word Model",
            status = when (wakeState) {
                WakeWordState.IDLE, WakeWordState.LISTENING -> DiagnosticStatus.OK
                WakeWordState.DETECTED -> DiagnosticStatus.INFO
                WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> DiagnosticStatus.ERROR
            },
            details = when (wakeState) {
                WakeWordState.IDLE -> "Loaded (idle)"
                WakeWordState.LISTENING -> "Active"
                WakeWordState.DETECTED -> "Detected"
                WakeWordState.MODEL_NOT_AVAILABLE -> "NOT FOUND"
                WakeWordState.ERROR -> "ERROR"
            }
        ))

        wakeWordDetector.unavailableReason.value?.let {
            items.add(DiagnosticItem(name = "Wake Word Reason", status = DiagnosticStatus.ERROR, details = it))
        }

        // Service
        val svcState = VasuForegroundService.serviceState.value
        items.add(DiagnosticItem(
            name = "Foreground Service",
            status = if (svcState.name == "LISTENING" || svcState.name == "ACTIVE") DiagnosticStatus.OK else DiagnosticStatus.INFO,
            details = svcState.name
        ))

        // Voice
        val customStatus = ttsManager.customVoiceStatus.value
        items.add(DiagnosticItem(
            name = "Custom Voice",
            status = when (customStatus) {
                VoiceModelStatus.ACTIVE_CUSTOM_MODEL, VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> DiagnosticStatus.OK
                VoiceModelStatus.FALLBACK_SYSTEM_TTS -> DiagnosticStatus.INFO
                VoiceModelStatus.ERROR -> DiagnosticStatus.ERROR
            },
            details = when (customStatus) {
                VoiceModelStatus.ACTIVE_CUSTOM_MODEL -> "Neural model active"
                VoiceModelStatus.ACTIVE_CUSTOM_SAMPLES -> "Custom samples loaded"
                VoiceModelStatus.FALLBACK_SYSTEM_TTS -> "System TTS fallback"
                VoiceModelStatus.ERROR -> "Error loading"
            }
        ))

        items.add(DiagnosticItem(name = "TTS Engine", status = DiagnosticStatus.INFO, details = ttsManager.state.value.name))
        items.add(DiagnosticItem(name = "Live Voice", status = DiagnosticStatus.INFO, details = geminiLiveVoiceService.voiceState.value.name))

        // Device
        items.add(DiagnosticItem(name = "Device", status = DiagnosticStatus.INFO, details = "${Build.MANUFACTURER} ${Build.MODEL}"))
        items.add(DiagnosticItem(name = "Android", status = DiagnosticStatus.INFO, details = "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"))

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        items.add(DiagnosticItem(
            name = "Audio Mode",
            status = DiagnosticStatus.INFO,
            details = when (audioManager?.mode) {
                AudioManager.MODE_NORMAL -> "Normal"
                AudioManager.MODE_IN_CALL -> "In Call"
                AudioManager.MODE_IN_COMMUNICATION -> "In Communication"
                AudioManager.MODE_RINGTONE -> "Ringtone"
                else -> "Unknown"
            }
        ))

        _uiState.value = DiagnosticsUiState(items = items)
    }
}
