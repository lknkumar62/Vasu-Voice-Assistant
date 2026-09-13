package com.vasu.assistant.core.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.vasu.assistant.core.ai.SecureKeyStore
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.wakeword.WakeWordDetector
import dagger.Lazy
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
 * 1. ONLINE: Gemini TTS (Kore female assistant voice)
 * 2. OFFLINE: Local TTS (custom assets / offline neural voice)
 * 3. LAST RESORT: Android Fallback TTS (only if explicitly enabled by user)
 *
 * Wake word echo protection: Mutes the wake word detector during ALL TTS
 * playback paths to prevent VASU's own voice from triggering a re-listen cycle.
 */
@Singleton
class VoiceRouter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: VasuSettings,
    private val keyStore: SecureKeyStore,
    private val geminiTtsEngine: GeminiTtsEngine,
    private val localTtsEngine: LocalTtsEngine,
    private val androidFallbackTtsEngine: AndroidFallbackTtsEngine,
    private val wakeWordDetector: Lazy<WakeWordDetector>
) {
    private val _currentSource = MutableStateFlow(ActiveVoiceSource.LOCAL_OFFLINE)
    val currentSource: StateFlow<ActiveVoiceSource> = _currentSource.asStateFlow()

    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    /**
     * Synthesize and speak text following online -> offline -> fallback priority.
     *
     * Priority:
     * 1. Gemini online (if key + network)
     * 2. Local offline (custom / Hindi TTS)
     * 3. Android system fallback
     *
     * If Gemini fails, we FALL BACK to local voice with a visible log/banner
     * instead of going MUTED silently. This fixes "response aata hai par voice nahi".
     * Wake word is muted during TTS to prevent self-trigger.
     */
    suspend fun speak(
        text: String,
        onStart: (() -> Unit)? = null,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ): Boolean {
        // Mute wake word detector during ALL TTS playback
        muteWakeWord(true)

        val wrappedOnDone: () -> Unit = {
            muteWakeWord(false)
            Log.i(TAG, "TTS_COMPLETED source=${_currentSource.value}")
            onDone?.invoke()
        }
        val wrappedOnError: (String) -> Unit = { error ->
            muteWakeWord(false)
            onError?.invoke(error)
        }

        // Fast-path connectivity check
        val online = isOnline() && !settings.offlineOnly.value
        val geminiConfigured = keyStore.hasGeminiKey()

        // 1. Online with Gemini configured — try Gemini first, fallback to local on failure
        if (online && geminiConfigured) {
            _currentSource.value = ActiveVoiceSource.GEMINI_ONLINE
            Log.d(TAG, "Routing turn to GeminiTtsEngine")

            val geminiSuccess = geminiTtsEngine.speak(
                text = text,
                onStart = onStart,
                onDone = wrappedOnDone,
                onError = { geminiError ->
                    Log.w(TAG, "TTS_ERROR provider=gemini category=$geminiError — falling back to LocalTtsEngine")
                    // Transparent fallback: not silent, logged and source changes to LOCAL
                    fallbackToLocal(text, onStart, wrappedOnDone, wrappedOnError)
                }
            )

            if (geminiSuccess) return true
            // If speak() returned false without invoking onError callback (e.g. immediate false),
            // fallback explicitly here too
            Log.w(TAG, "GeminiTtsEngine returned false, falling back to local Vasu voice")
            _currentSource.value = ActiveVoiceSource.LOCAL_OFFLINE
            val localFallbackSuccess = localTtsEngine.speak(
                text = text,
                onStart = onStart,
                onDone = wrappedOnDone,
                onError = { localErr ->
                    Log.w(TAG, "Local Vasu voice also failed: $localErr")
                    if (settings.androidFallbackTtsEnabled.value) {
                        fallbackToAndroidSystem(text, onStart, wrappedOnDone, wrappedOnError)
                    } else {
                        _currentSource.value = ActiveVoiceSource.MUTED
                        wrappedOnError("Vasu voice unavailable: $localErr")
                    }
                }
            )
            if (localFallbackSuccess) return true
            if (settings.androidFallbackTtsEnabled.value) {
                return fallbackToAndroidSystem(text, onStart, wrappedOnDone, wrappedOnError)
            }
            _currentSource.value = ActiveVoiceSource.MUTED
            wrappedOnError("No Vasu voice available and local fallback disabled")
            return false
        }

        // 2. Offline / Local TTS — Vasu voice only, no Android fallback unless enabled (Maya parity)
        Log.d(TAG, "Routing turn to LocalTtsEngine (Vasu voice, offline or Gemini not ready)")
        _currentSource.value = ActiveVoiceSource.LOCAL_OFFLINE
        val localSuccess = localTtsEngine.speak(
            text = text,
            onStart = onStart,
            onDone = wrappedOnDone,
            onError = { localError ->
                Log.w(TAG, "LocalTtsEngine failed: $localError")
                if (settings.androidFallbackTtsEnabled.value) {
                    fallbackToAndroidSystem(text, onStart, wrappedOnDone, wrappedOnError)
                } else {
                    _currentSource.value = ActiveVoiceSource.MUTED
                    wrappedOnError(localError)
                }
            }
        )

        if (localSuccess) return true

        // 3. Android system fallback — ONLY if explicitly enabled (user wants no local voice, so disabled by default)
        if (settings.androidFallbackTtsEnabled.value) {
            Log.d(TAG, "Local Vasu voice failed, trying Android fallback (enabled)")
            return fallbackToAndroidSystem(text, onStart, wrappedOnDone, wrappedOnError)
        }
        _currentSource.value = ActiveVoiceSource.MUTED
        wrappedOnError("No Vasu voice available and Android fallback disabled (as requested)")
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
            val success = localTtsEngine.speak(
                text = text,
                onStart = onStart,
                onDone = onDone,
                onError = { localErr ->
                    Log.w(TAG, "LocalTtsEngine fallback failed: $localErr")
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
        Log.i(TAG, "Engaging emergency AndroidFallbackTtsEngine")
        CoroutineScope(Dispatchers.Main).launch {
            androidFallbackTtsEngine.speak(text, onStart, onDone, onError)
        }
        return true
    }

    fun stop() {
        geminiTtsEngine.stop()
        localTtsEngine.stop()
        androidFallbackTtsEngine.stop()
        muteWakeWord(false)
    }

    /**
     * Mute or unmute the wake word detector to prevent echo during TTS playback.
     */
    private fun muteWakeWord(mute: Boolean) {
        try {
            wakeWordDetector.get().setMutedForPlayback(mute)
            Log.d(TAG, "Wake word detector muted=$mute during TTS")
        } catch (e: Exception) {
            Log.w(TAG, "Could not mute/unmute wake word detector: ${e.message}")
        }
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
