package com.vasu.assistant.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * AudioSessionManager — Central microphone mutex for the entire VASU audio pipeline.
 *
 * Root cause: WakeWordDetector, STTManager, GeminiLiveVoiceService, and NativeMicrophoneRecorder
 * each create their own AudioRecord independently. When two components try to hold the mic
 * simultaneously, Android throws ERROR_RECOGNIZER_BUSY, ERROR_CLIENT, or
 * "Speech Recognition cannot record" / "Could not process audio" errors.
 *
 * This manager enforces mutual exclusion by:
 * 1. Tracking mic ownership (only one owner at a time)
 * 2. Coordinating state transitions (IDLE → WAKE_LISTENING → COMMAND_LISTENING → PROCESSING → SPEAKING → FOLLOW_UP → ...)
 * 3. Managing AudioFocus (request/release between recording and playback)
 * 4. Detecting external mic contention (other apps)
 * 5. Providing clean acquire/release with retry + exponential backoff
 */
@Singleton
class AudioSessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AudioSessionManager"
    }

    // ── Mic ownership ──────────────────────────────────────────────────

    enum class MicOwner(val label: String) {
        NONE("none"),
        WAKE_WORD_DETECTOR("wake_word_detector"),
        SPEECH_RECOGNIZER("speech_recognizer"),
        GEMINI_LIVE("gemini_live"),
        NATIVE_RECORDER("native_recorder"),
        TTS_PLAYBACK("tts_playback"),
        EXTERNAL_APP("external_app")
    }

    // ── Session state ──────────────────────────────────────────────────

    enum class SessionState(val label: String) {
        IDLE("idle"),
        WAKE_LISTENING("wake_listening"),
        COMMAND_LISTENING("command_listening"),
        PROCESSING("processing"),
        SPEAKING("speaking"),
        FOLLOW_UP_LISTENING("follow_up_listening"),
        STOPPED("stopped"),
        ERROR("error")
    }

    // ── Internal state ─────────────────────────────────────────────────

    private val lock = ReentrantLock()
    private val _currentOwner = MutableStateFlow(MicOwner.NONE)
    val currentOwner: StateFlow<MicOwner> = _currentOwner.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionState.IDLE)
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    private val _isMicAvailable = MutableStateFlow(true)
    val isMicAvailable: StateFlow<Boolean> = _isMicAvailable.asStateFlow()

    private val _externalMicOccupied = MutableStateFlow(false)
    val externalMicOccupied: StateFlow<Boolean> = _externalMicOccupied.asStateFlow()

    private val _audioFocusHeld = MutableStateFlow(false)
    val audioFocusHeld: StateFlow<Boolean> = _audioFocusHeld.asStateFlow()

    private var lastOwnerSwitchTime = AtomicLong(0)
    private val MIN_OWNER_SWITCH_DELAY_MS = 150L

    private val pendingReleaseWaiters = CopyOnWriteArrayList<kotlinx.coroutines.CompletableDeferred<Unit>>()

    // ── AudioFocus ─────────────────────────────────────────────────────

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusRequest: AudioFocusRequest by lazy {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { focusChange ->
                Log.d(TAG, "AudioFocus changed: $focusChange")
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        _audioFocusHeld.value = false
                        // Another app took the mic — release if we hold it
                        if (_currentOwner.value != MicOwner.NONE && _currentOwner.value != MicOwner.EXTERNAL_APP) {
                            Log.w(TAG, "AudioFocus lost — another app needs mic")
                            forceReleaseMic("audio_focus_lost")
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        _audioFocusHeld.value = true
                    }
                }
            }
            .build()
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            val externalRecording = configs?.any {
                it.isClientSilenced()
            } ?: false

            val otherAppRecording = configs?.any { config ->
                // Check if any non-VASU app is recording
                config.clientAudioSource != MediaRecorder.AudioSource.VOICE_RECOGNITION &&
                config.clientAudioSource != MediaRecorder.AudioSource.MIC
            } ?: false

            _externalMicOccupied.value = otherAppRecording || externalRecording

            if (otherAppRecording) {
                Log.w(TAG, "External app has microphone — VASU should wait")
                if (_currentOwner.value != MicOwner.NONE && _currentOwner.value != MicOwner.EXTERNAL_APP) {
                    forceReleaseMic("external_app_recording")
                }
            }
        }
    }

    init {
        audioManager.registerAudioRecordingCallback(recordingCallback, null)
        Log.d(TAG, "AudioSessionManager initialized")
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Request mic ownership for a component.
     * Returns true if granted, false if denied/busy.
     * If currently held by another component, releases the old owner first.
     */
    fun requestMic(owner: MicOwner, timeoutMs: Long = 2000): Boolean = lock.withLock {
        Log.d(TAG, "requestMic($owner) — current=${_currentOwner.value}, state=${_sessionState.value}")

        // Already own it
        if (_currentOwner.value == owner) {
            Log.d(TAG, "requestMic($owner) — already owner, granting")
            return@withLock true
        }

        // External app has mic — wait or fail
        if (_externalMicOccupied.value) {
            Log.w(TAG, "requestMic($owner) — external app has mic, waiting up to ${timeoutMs}ms")
            val start = System.currentTimeMillis()
            while (_externalMicOccupied.value && (System.currentTimeMillis() - start) < timeoutMs) {
                Thread.sleep(50)
            }
            if (_externalMicOccupied.value) {
                Log.e(TAG, "requestMic($owner) — external app still has mic, denying")
                return@withLock false
            }
        }

        // Someone else owns it — release with grace period
        if (_currentOwner.value != MicOwner.NONE) {
            val prevOwner = _currentOwner.value
            Log.d(TAG, "requestMic($owner) — releasing previous owner: $prevOwner")
            releaseMicInternal(prevOwner, forceRelease = true)
        }

        // Enforce minimum delay between owner switches
        val now = System.currentTimeMillis()
        val elapsed = now - lastOwnerSwitchTime.get()
        if (elapsed < MIN_OWNER_SWITCH_DELAY_MS) {
            Thread.sleep(MIN_OWNER_SWITCH_DELAY_MS - elapsed)
        }

        // Request audio focus
        val focusResult = audioManager.requestAudioFocus(audioFocusRequest)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.w(TAG, "requestMic($owner) — AudioFocus denied, proceeding without focus")
        }
        _audioFocusHeld.value = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        _currentOwner.value = owner
        lastOwnerSwitchTime.set(System.currentTimeMillis())
        Log.i(TAG, "requestMic($owner) — GRANTED")
        true
    }

    /**
     * Release mic ownership. Only the current owner can release.
     */
    fun releaseMic(owner: MicOwner) = lock.withLock {
        if (_currentOwner.value != owner) {
            Log.w(TAG, "releaseMic($owner) — not current owner (owner=${_currentOwner.value}), ignoring")
            return@withLock
        }
        releaseMicInternal(owner, forceRelease = false)
    }

    /**
     * Force-release regardless of who owns it. Used for emergency cleanup.
     */
    fun forceReleaseMic(reason: String) = lock.withLock {
        val prev = _currentOwner.value
        if (prev == MicOwner.NONE) return@withLock
        Log.w(TAG, "forceReleaseMic($prev, reason=$reason)")
        releaseMicInternal(prev, forceRelease = true)
    }

    /**
     * Transition session state (e.g., WAKE_LISTENING → COMMAND_LISTENING).
     */
    fun transitionState(newState: SessionState) {
        val prev = _sessionState.value
        _sessionState.value = newState
        Log.d(TAG, "State transition: ${prev.label} → ${newState.label}")
    }

    /**
     * Check if mic is currently available (not held by any component and not externally occupied).
     */
    fun isMicFree(): Boolean {
        return _currentOwner.value == MicOwner.NONE && !_externalMicOccupied.value
    }

    /**
     * Wait until mic becomes free (with timeout).
     */
    suspend fun waitForMicFree(timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (!isMicFree() && (System.currentTimeMillis() - start) < timeoutMs) {
            kotlinx.coroutines.delay(50)
        }
        return isMicFree()
    }

    /**
     * Acquire AudioFocus without mic ownership (for TTS playback only).
     */
    fun acquireAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest)
        _audioFocusHeld.value = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Release AudioFocus without releasing mic ownership.
     */
    fun releaseAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
        _audioFocusHeld.value = false
    }

    /**
     * Clean up all resources. Call from service.onDestroy().
     */
    fun destroy() {
        Log.d(TAG, "destroy()")
        forceReleaseMic("service_destroy")
        audioManager.unregisterAudioRecordingCallback(recordingCallback)
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    // ── Internal ───────────────────────────────────────────────────────

    private fun releaseMicInternal(owner: MicOwner, forceRelease: Boolean) {
        if (_currentOwner.value != owner && !forceRelease) {
            return
        }
        _currentOwner.value = MicOwner.NONE
        lastOwnerSwitchTime.set(System.currentTimeMillis())

        // Abandon audio focus if we're the last user
        if (_audioFocusHeld.value) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest)
            _audioFocusHeld.value = false
        }

        // Wake any waiters
        val waiters = ArrayList(pendingReleaseWaiters)
        pendingReleaseWaiters.clear()
        waiters.forEach { it.complete(Unit) }

        Log.d(TAG, "Mic released (was $owner)")
    }
}
