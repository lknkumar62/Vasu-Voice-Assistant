package com.vasu.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIClient
import com.vasu.assistant.core.ai.AiResult
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.network.NetworkMonitor
import com.vasu.assistant.core.network.NetworkState
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.tts.VoiceProfile
import com.vasu.assistant.core.tts.VoiceStatus
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Result of the last Test Connection press. */
enum class ConnectionTest { NOT_TESTED, TESTING, PASSED, FAILED }

data class SettingsUiState(
    // AI provider
    val geminiEnabled: Boolean = false,
    val hasKey: Boolean = false,
    val maskedKey: String? = null,
    val model: String = SecureKeyStore.DEFAULT_MODEL,
    val keyStoreAvailable: Boolean = true,
    val connectionTest: ConnectionTest = ConnectionTest.NOT_TESTED,
    val connectionMessage: String = "",
    val lastSuccessfulConnection: Long = 0L,
    val lastError: String? = null,
    val network: NetworkState = NetworkState.OFFLINE,

    // Voice
    val voiceProfile: VoiceProfile = VoiceProfile.VASU_DEFAULT,
    val installedVoices: List<String> = emptyList(),
    val voiceStatus: VoiceStatus = VoiceStatus(),

    // Wake word
    val wakeWordEnabled: Boolean = false,
    val wakeWordState: WakeWordState = WakeWordState.IDLE,

    val voiceGuardEnabled: Boolean = false
) {
    /** Cloud AI can only work when it is switched on, keyed, and reachable. */
    val cloudUsable: Boolean
        get() = geminiEnabled && hasKey && network != NetworkState.OFFLINE
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: SecureKeyStore,
    private val aiClient: AIClient,
    private val settings: VasuSettings,
    private val ttsManager: TTSManager,
    private val wakeWordDetector: WakeWordDetector,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        ttsManager.initialize()

        viewModelScope.launch {
            networkMonitor.state.collect { _uiState.value = _uiState.value.copy(network = it) }
        }
        viewModelScope.launch {
            wakeWordDetector.state.collect { _uiState.value = _uiState.value.copy(wakeWordState = it) }
        }
        viewModelScope.launch {
            settings.voiceProfile.collect { _uiState.value = _uiState.value.copy(voiceProfile = it) }
        }
        viewModelScope.launch {
            ttsManager.availableLanguages.collect { locales ->
                _uiState.value = _uiState.value.copy(
                    installedVoices = locales.map { it.toLanguageTag() }.distinct()
                )
            }
        }
        viewModelScope.launch {
            ttsManager.voiceStatus.collect { _uiState.value = _uiState.value.copy(voiceStatus = it) }
        }
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(
            geminiEnabled = keyStore.geminiEnabled,
            hasKey = keyStore.hasGeminiKey(),
            maskedKey = keyStore.maskedGeminiKey(),
            model = keyStore.geminiModel,
            keyStoreAvailable = keyStore.isAvailable,
            lastSuccessfulConnection = keyStore.lastSuccessfulConnection,
            lastError = keyStore.lastError,
            voiceProfile = settings.voiceProfile.value,
            wakeWordEnabled = settings.wakeWordEnabled.value,
            voiceGuardEnabled = settings.voiceGuardEnabled.value,
            network = networkMonitor.state.value
        )
    }

    // AI provider

    fun setGeminiEnabled(enabled: Boolean) {
        keyStore.geminiEnabled = enabled
        refresh()
    }

    fun saveKey(key: String) {
        if (key.isBlank()) {
            _uiState.value = _uiState.value.copy(
                connectionTest = ConnectionTest.FAILED,
                connectionMessage = "Enter a key first."
            )
            return
        }
        val saved = aiClient.saveApiKey(key)
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = if (saved) "Key saved. Test the connection to confirm it works."
            else "Could not save the key - secure storage is unavailable on this device."
        )
        refresh()
    }

    fun removeKey() {
        aiClient.removeApiKey()
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = "Key removed."
        )
        refresh()
    }

    fun setModel(model: String) {
        keyStore.geminiModel = model
        // A model change invalidates the previous result: the old model may work
        // for this key while the new one is not enabled for it.
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = ""
        )
        refresh()
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                connectionTest = ConnectionTest.TESTING,
                connectionMessage = "Contacting Gemini..."
            )
            val (passed, message) = when (val result = aiClient.testConnection()) {
                is AiResult.Text -> true to result.content
                is AiResult.Failure -> false to result.message
                is AiResult.FunctionCall -> false to "Unexpected response from Gemini."
            }
            _uiState.value = _uiState.value.copy(
                connectionTest = if (passed) ConnectionTest.PASSED else ConnectionTest.FAILED,
                connectionMessage = message
            )
            refresh()
        }
    }

    // Voice

    fun setLanguage(tag: String) = updateProfile { it.copy(language = tag, isHindi = tag.startsWith("hi"), isEnglish = tag.startsWith("en")) }

    fun setPitch(value: Float) = updateProfile { it.copy(pitch = value) }

    fun setSpeechRate(value: Float) = updateProfile { it.copy(speechRate = value) }

    fun setVolume(value: Float) = updateProfile { it.copy(volume = value) }

    private fun updateProfile(transform: (VoiceProfile) -> VoiceProfile) {
        val updated = transform(settings.voiceProfile.value)
        settings.setVoiceProfile(updated)
        ttsManager.applyProfile(updated)
    }

    /** Speaks a Hinglish line so the user hears the actual configured voice. */
    fun testVoice() {
        ttsManager.applyProfile(settings.voiceProfile.value)
        ttsManager.speak(
            if (settings.voiceProfile.value.isHindi) "Ji, main VASU hoon. Bataiye kya karna hai."
            else "Hi, I am VASU. Tell me what you need."
        )
    }

    // Wake word

    /**
     * Persists the choice and starts or stops the foreground service that owns the
     * detector. The service reports its own state, so if the detection model is
     * missing the UI shows that rather than claiming the wake word is live.
     */
    fun setWakeWordEnabled(enabled: Boolean) {
        settings.setWakeWordEnabled(enabled)
        if (enabled) {
            wakeWordDetector.initialize()
            VasuForegroundService.start(context)
        } else {
            VasuForegroundService.stop(context)
            wakeWordDetector.stop()
        }
        _uiState.value = _uiState.value.copy(wakeWordEnabled = enabled)
    }

    fun setVoiceGuardEnabled(enabled: Boolean) {
        settings.setVoiceGuardEnabled(enabled)
        _uiState.value = _uiState.value.copy(voiceGuardEnabled = enabled)
    }

    // System screens. These are the only way to grant accessibility and
    // notification access; no in-app toggle can do it.

    fun openAccessibilitySettings() = launchSystem(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)

    fun openNotificationAccessSettings() =
        launchSystem("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    fun openTtsSettings() = launchSystem("com.android.settings.TTS_SETTINGS")

    fun openAppSettings() {
        val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun launchSystem(action: String) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (!opened) {
            _uiState.value = _uiState.value.copy(
                connectionMessage = "This device has no screen for $action."
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
