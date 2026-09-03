package com.vasu.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.AIClient
import com.vasu.assistant.core.ai.AiProviderConfig
import com.vasu.assistant.core.ai.AiResult
import com.vasu.assistant.core.ai.ModelCatalog
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
    /**
     * What the key can actually use. Falls back to the configured chain until the
     * catalogue has been read, so the picker never invents a model.
     */
    val availableModels: List<String> =
        AiProviderConfig.GEMINI.candidatesFor(SecureKeyStore.DEFAULT_MODEL),
    val modelsDiscovered: Boolean = false,
    val modelsRefreshing: Boolean = false,
    val modelMessage: String = "",
    /** Set when a fallback answered instead of [model], so the swap is visible. */
    val activeModel: String? = null,
    val allowModelFallback: Boolean = AiProviderConfig.GEMINI.allowFallback,
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
    val customVoiceStatus: com.vasu.assistant.core.tts.VoiceModelStatus = com.vasu.assistant.core.tts.VoiceModelStatus.FALLBACK_SYSTEM_TTS,
    val androidFallbackTtsEnabled: Boolean = false,
    val geminiTtsVoice: String = VasuSettings.DEFAULT_GEMINI_TTS_VOICE,
    val geminiTtsModel: String = VasuSettings.DEFAULT_GEMINI_TTS_MODEL,

    // Wake word
    val wakeWordEnabled: Boolean = false,
    val wakeWordState: WakeWordState = WakeWordState.IDLE,
    val wakeWordReason: String? = null,

    val voiceGuardEnabled: Boolean = false,
    val autoAllowEnabled: Boolean = true
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
            wakeWordDetector.unavailableReason.collect {
                _uiState.value = _uiState.value.copy(wakeWordReason = it)
            }
        }
        viewModelScope.launch {
            settings.voiceProfile.collect { _uiState.value = _uiState.value.copy(voiceProfile = it) }
        }
        viewModelScope.launch {
            settings.autoAllowEnabled.collect { _uiState.value = _uiState.value.copy(autoAllowEnabled = it) }
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
        viewModelScope.launch {
            ttsManager.customVoiceStatus.collect { _uiState.value = _uiState.value.copy(customVoiceStatus = it) }
        }
    }

    private fun refresh() {
        val discovered = keyStore.discoveredModels
        _uiState.value = _uiState.value.copy(
            geminiEnabled = keyStore.geminiEnabled,
            hasKey = keyStore.hasGeminiKey(),
            maskedKey = keyStore.maskedGeminiKey(),
            model = keyStore.geminiModel,
            availableModels = if (discovered.isEmpty()) {
                AiProviderConfig.GEMINI.candidatesFor(keyStore.geminiModel)
            } else {
                discovered.sorted()
            },
            modelsDiscovered = discovered.isNotEmpty(),
            activeModel = keyStore.activeModel,
            allowModelFallback = keyStore.allowModelFallback,
            keyStoreAvailable = keyStore.isAvailable,
            lastSuccessfulConnection = keyStore.lastSuccessfulConnection,
            lastError = keyStore.lastError,
            voiceProfile = settings.voiceProfile.value,
            wakeWordEnabled = settings.wakeWordEnabled.value,
            voiceGuardEnabled = settings.voiceGuardEnabled.value,
            autoAllowEnabled = settings.autoAllowEnabled.value,
            androidFallbackTtsEnabled = settings.androidFallbackTtsEnabled.value,
            geminiTtsVoice = settings.geminiTtsVoice.value,
            geminiTtsModel = settings.geminiTtsModel.value,
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
        // Which models are available is a property of the key, so a cached
        // catalogue from the previous one would filter the new key's chain wrongly.
        forgetModelCatalog()
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = if (saved) "Key saved. Test the connection to confirm it works."
            else "Could not save the key - secure storage is unavailable on this device."
        )
        refresh()
    }

    fun removeKey() {
        aiClient.removeApiKey()
        forgetModelCatalog()
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = "Key removed."
        )
        refresh()
    }

    private fun forgetModelCatalog() {
        keyStore.discoveredModels = emptySet()
        keyStore.activeModel = null
        _uiState.value = _uiState.value.copy(modelMessage = "")
    }

    fun setModel(model: String) {
        keyStore.geminiModel = model
        // A model change invalidates the previous result: the old model may work
        // for this key while the new one is not enabled for it.
        keyStore.activeModel = null
        _uiState.value = _uiState.value.copy(
            connectionTest = ConnectionTest.NOT_TESTED,
            connectionMessage = ""
        )
        refresh()
    }

    /**
     * Reads the provider's model list for this key. Without it the picker can only
     * offer guesses, which is how a model the key cannot use came to be the default.
     */
    fun refreshModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                modelsRefreshing = true,
                modelMessage = "Asking Google which models this key can use..."
            )
            val message = when (val catalog = aiClient.refreshModels()) {
                is ModelCatalog.Available -> {
                    val chat = catalog.chatModelIds
                    if (chat.isEmpty()) "This key has no chat-capable models."
                    else "${chat.size} models available for this key."
                }
                is ModelCatalog.Unavailable -> catalog.message
            }
            _uiState.value = _uiState.value.copy(
                modelsRefreshing = false,
                modelMessage = message
            )
            refresh()
        }
    }

    fun setAllowModelFallback(enabled: Boolean) {
        keyStore.allowModelFallback = enabled
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

    /** Speaks a natural Hindi phrase so the user hears the actual configured voice. */
    fun testVoice() {
        ttsManager.applyProfile(settings.voiceProfile.value)
        ttsManager.speak(
            if (settings.voiceProfile.value.isHindi) "नमस्ते, मैं वासु हूँ। बताइए मैं आपकी क्या मदद करूँ?"
            else "Hello, I am VASU. How can I help you today?"
        )
    }

    fun setAndroidFallbackTtsEnabled(enabled: Boolean) {
        settings.setAndroidFallbackTtsEnabled(enabled)
        _uiState.value = _uiState.value.copy(androidFallbackTtsEnabled = enabled)
    }

    fun setGeminiTtsVoice(voice: String) {
        settings.setGeminiTtsVoice(voice)
        _uiState.value = _uiState.value.copy(geminiTtsVoice = voice)
    }

    fun setGeminiTtsModel(model: String) {
        settings.setGeminiTtsModel(model)
        _uiState.value = _uiState.value.copy(geminiTtsModel = model)
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

    fun setAutoAllowEnabled(enabled: Boolean) {
        settings.setAutoAllowEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoAllowEnabled = enabled)
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
