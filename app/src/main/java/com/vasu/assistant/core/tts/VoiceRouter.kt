package com.vasu.assistant.core.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class ActiveVoiceSource(val displayName: String) {
    GEMINI_ONLINE("Gemini Online Female TTS"),
    LOCAL_OFFLINE("Local Offline Voice"),
    ANDROID_FALLBACK("Android System Fallback"),
    MUTED("No Voice Available")
}

/**
 * VoiceRouter - Intelligent online/offline speech synthesis router.
 *
 * Priority routing:
 * 1. ONLINE PRIMARY: Gemini Live native AUDIO (Kore female assistant voice -> 24kHz PCM -> AudioTrack)
 * 2. ONLINE SECONDARY: Gemini REST TTS (Kore female assistant voice)
 * 3. OFFLINE: Local TTS (custom assets / offline neural voice)
 * 4. LAST RESORT: Android Fallback TTS (only if explicitly enabled by user)
 */
@Singleton
class VoiceRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VasuSettings,
    private val keyStore: SecureKeyStore,
    private val geminiLiveVoiceService: GeminiLiveVoiceService,
    private val geminiTtsEngine: GeminiTtsEngine,
    private val localTtsEngine: LocalTtsEngine,
    private val androidFallbackTtsEngine: AndroidFallbackTtsEngine
) {
    private val _currentSource = MutableStateFlow(ActiveVoiceSource.LOCAL_OFFLINE)
    val currentSource: StateFlow<ActiveVoiceSource> = _currentSource.asStateFlow()

    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    /**
     * Synthesize and speak text following the strict online -> offline -> emergency fallback priority.
     */
    suspend fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        // Fast-path connectivity check
        val online = isOnline() && !settings.offlineOnly.value
        val geminiConfigured = keyStore.hasGeminiKey()

        // 1. Online with Gemini configured: Primary route is Gemini Live native Kore audio
        if (online && geminiConfigured) {
            _currentSource.value = ActiveVoiceSource.GEMINI_ONLINE
            Log.d(TAG, "[GEMINI_KORE_AUDIO] Routing speech turn to GeminiLiveVoiceService (Kore 24kHz PCM -> AudioTrack)")

            val liveSuccess = geminiLiveVoiceService.speakText(
                text = text,
                onStart = onStart,
                onDone = onDone,
                onError = { liveError ->
                    Log.w(TAG, "[GEMINI_KORE_AUDIO] Gemini Live synthesis failed ($liveError); trying GeminiTtsEngine fallback")
                }
            )

            if (liveSuccess) return true

            Log.d(TAG, "[GEMINI_KORE_AUDIO] Routing speech turn to GeminiTtsEngine fallback")
            val geminiSuccess = geminiTtsEngine.speak(
                text = text,
                onStart = onStart,
                onDone = onDone,
                onError = { geminiError ->
                    Log.w(TAG, "[GEMINI_KORE_AUDIO] Gemini TTS failed ($geminiError); falling back to LocalTtsEngine")
                    fallbackToLocal(text, onStart, onDone, onError)
                }
            )

            if (geminiSuccess) return true
        }

        // 2. Offline / Local TTS fallback
        val reason = when {
            !online -> "Device offline or offline-only mode active"
            !geminiConfigured -> "Gemini API key is not configured"
            else -> "Gemini Live and REST audio generation both failed"
        }
        Log.w(TAG, "[LOCAL_ANDROID_TTS_FALLBACK] Gemini audio unavailable ($reason). Routing turn to LocalTtsEngine")
        _currentSource.value = ActiveVoiceSource.LOCAL_OFFLINE
        val localSuccess = localTtsEngine.speak(
            text = text,
            onStart = onStart,
            onDone = onDone,
            onError = { localError ->
                Log.w(TAG, "[LOCAL_ANDROID_TTS_FALLBACK] LocalTtsEngine failed: $localError")
                if (settings.androidFallbackTtsEnabled.value) {
                    fallbackToAndroidSystem(text, onStart, onDone, onError)
                } else {
                    _currentSource.value = ActiveVoiceSource.MUTED
                    onError?.invoke(localError)
                }
            }
        )

        if (localSuccess) return true

        // 3. Emergency Android system fallback (only if user explicitly enabled it)
        if (settings.androidFallbackTtsEnabled.value) {
            return fallbackToAndroidSystem(text, onStart, onDone, onError)
        }

        _currentSource.value = ActiveVoiceSource.MUTED
        val err = "No voice playback available (Gemini offline/unconfigured, Local TTS unavailable, Android fallback disabled)"
        Log.w(TAG, err)
        onError?.invoke(err)
        return false
    }

    private fun fallbackToLocal(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            _currentSource.value = ActiveVoiceSource.LOCAL_OFFLINE
            Log.w(TAG, "[LOCAL_ANDROID_TTS_FALLBACK] Executing fallback to LocalTtsEngine")
            val success = localTtsEngine.speak(
                text = text,
                onStart = onStart,
                onDone = onDone,
                onError = { localErr ->
                    Log.w(TAG, "[LOCAL_ANDROID_TTS_FALLBACK] LocalTtsEngine fallback failed: $localErr")
                    if (settings.androidFallbackTtsEnabled.value) {
                        fallbackToAndroidSystem(text, onStart, onDone, onError)
                    } else {
                        _currentSource.value = ActiveVoiceSource.MUTED
                        onError?.invoke(localErr)
                    }
                }
            )
            if (!success && settings.androidFallbackTtsEnabled.value) {
                fallbackToAndroidSystem(text, onStart, onDone, onError)
            }
        }
    }

    private fun fallbackToAndroidSystem(
        text: String,
        onStart: (() -> Unit)?,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ): Boolean {
        _currentSource.value = ActiveVoiceSource.ANDROID_FALLBACK
        Log.i(TAG, "[LOCAL_ANDROID_TTS_FALLBACK] Engaging emergency AndroidFallbackTtsEngine")
        CoroutineScope(Dispatchers.Main).launch {
            androidFallbackTtsEngine.speak(text, onStart, onDone, onError)
        }
        return true
    }

    fun stop() {
        geminiLiveVoiceService.stopSpeaking()
        geminiTtsEngine.stop()
        localTtsEngine.stop()
        androidFallbackTtsEngine.stop()
    }

    private fun isOnline(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "VoiceRouter"
    }
}
