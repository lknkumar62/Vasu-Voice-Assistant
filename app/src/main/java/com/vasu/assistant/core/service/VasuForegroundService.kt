package com.vasu.assistant.core.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.core.ai.AIOrchestrator
import com.vasu.assistant.core.audio.AudioSessionManager
import com.vasu.assistant.core.automation.AutomationEngine
import com.vasu.assistant.core.automation.MissionEngine
import com.vasu.assistant.core.settings.VasuSettings
import com.vasu.assistant.core.stt.STTManager
import com.vasu.assistant.core.tts.TTSManager
import com.vasu.assistant.core.voice.GeminiLiveVoiceService
import com.vasu.assistant.core.voice.GeminiVoiceState
import com.vasu.assistant.core.voice.NativeAudioPlayer
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.devices.DeviceControlManager
import com.vasu.assistant.ui.overlay.AssistantOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * VasuServiceState - Strict authoritative state machine for VASU background service:
 * OFF -> STARTING -> LISTENING -> ACTIVATING -> ACTIVE -> SPEAKING -> STOPPING -> ERROR
 */
enum class VasuServiceState {
    OFF,
    STARTING,
    LISTENING,
    ACTIVATING,
    ACTIVE,
    SPEAKING,
    STOPPING,
    ERROR
}

/**
 * VasuForegroundService - The single authoritative long-running foreground service for VASU.
 *
 * Coordinates:
 * 1. PATH A: UI -> VoiceManager -> Gemini Live Session -> 24kHz Native PCM -> NativeAudioPlayer -> speaker
 * 2. PATH B: Wake word toggle -> Permission -> Native Mic -> VAD -> WakeWordDetector (hello_vasu.tflite) -> ACTIVE
 * 3. PATH C: Wake word -> Command capture -> AIOrchestrator (<50ms native / Gemini) -> Kore response -> speaker -> LISTENING
 *
 * Conversation loop:
 * Hello Vasu → wake detect → release mic → STT/Gemini acquires → listen → process → TTS speaks → mic released → back to LISTENING
 */
@AndroidEntryPoint
class VasuForegroundService : Service() {

    @Inject lateinit var wakeWordListener: WakeWordDetector
    @Inject lateinit var geminiLiveVoiceService: GeminiLiveVoiceService
    @Inject lateinit var nativeAudioPlayer: NativeAudioPlayer
    @Inject lateinit var aiOrchestrator: AIOrchestrator
    @Inject lateinit var deviceControlManager: DeviceControlManager
    @Inject lateinit var sttManager: STTManager
    @Inject lateinit var ttsManager: TTSManager
    @Inject lateinit var audioSessionManager: AudioSessionManager
    @Inject lateinit var settings: VasuSettings
    @Inject lateinit var automationEngine: AutomationEngine
    @Inject lateinit var missionEngine: MissionEngine

    private val channelId = "vasu_service"
    private val notificationId = 1001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wakeLock: PowerManager.WakeLock? = null
    private var observerJob: Job? = null

    companion object {
        private const val TAG = "VasuForegroundService"

        const val ACTION_START = "com.vasu.assistant.START"
        const val ACTION_STOP = "com.vasu.assistant.STOP"
        const val ACTION_UPDATE = "com.vasu.assistant.UPDATE"
        const val ACTION_START_VOICE = "com.vasu.assistant.START_VOICE"
        const val ACTION_INCOMING_CALL = "INCOMING_CALL"
        const val EXTRA_STATUS = "status"
        const val EXTRA_CALL_STATE = "call_state"
        const val EXTRA_RESUME_WAKE_WORD = "vasu_resume_wake_word"

        private val _serviceState = MutableStateFlow(VasuServiceState.OFF)
        val serviceState: StateFlow<VasuServiceState> = _serviceState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, VasuForegroundService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(Intent(context, VasuForegroundService::class.java).apply { action = ACTION_STOP })
            } catch (e: IllegalStateException) {
                // Service not running and we are in the background: nothing to stop.
                Log.w(TAG, "stop() ignored (background start blocked): ${e.message}")
            }
        }

        fun startVoiceInteraction(context: Context) {
            val intent = Intent(context, VasuForegroundService::class.java).apply { action = ACTION_START_VOICE }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateStatus(context: Context, status: String) {
            try {
                context.startService(Intent(context, VasuForegroundService::class.java).apply {
                    action = ACTION_UPDATE
                    putExtra(EXTRA_STATUS, status)
                })
            } catch (e: IllegalStateException) {
                // Service not running and we are in the background: nothing to update.
                Log.w(TAG, "updateStatus() ignored (background start blocked): ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "vasu:assistant_wake_lock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)
            }
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_STATUS) ?: "VASU is active"
                notifyNotification(text)
            }
            ACTION_START_VOICE -> {
                triggerActiveVoiceSession()
            }
            ACTION_INCOMING_CALL -> {
                handleIncomingCall(intent)
            }
            // Null intent = system restarted the service (START_STICKY): resume listening.
            null, ACTION_START -> beginWakeWordPipeline()
            else -> {
                // Unknown/external actions must not silently start a microphone
                // pipeline. Satisfy the startForegroundService contract without
                // touching the live notification of an already-running service.
                Log.w(TAG, "Unhandled service action: ${intent?.action}")
                if (_serviceState.value == VasuServiceState.OFF) {
                    goForeground("VASU idle", withMic = false)
                }
            }
        }
        return START_STICKY
    }

    private fun beginWakeWordPipeline() {
        _serviceState.value = VasuServiceState.STARTING

        if (!hasMicPermission()) {
            _serviceState.value = VasuServiceState.ERROR
            goForeground("Microphone permission needed - open VASU to grant it", withMic = false)
            return
        }

        if (!goForeground("VASU active - Listening for \"Hello Vasu\"", withMic = true)) {
            // Mic foreground service disallowed from here (background start on
            // Android 11+, mic FGS prerequisites on Android 14+). The notification
            // now asks the user to tap and resume from the foreground.
            _serviceState.value = VasuServiceState.ERROR
            return
        }
        wakeWordListener.initialize()

        when (wakeWordListener.state.value) {
            WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> {
                _serviceState.value = VasuServiceState.ERROR
                notifyNotification(wakeWordListener.unavailableReason.value ?: "Wake word unavailable")
            }
            else -> {
                if (!wakeWordListener.start()) {
                    // AudioRecord could not start (mic busy, or a background
                    // start on Android 11+ where capture is not allowed).
                    Log.w(TAG, "Wake word listener failed to start")
                    _serviceState.value = VasuServiceState.ERROR
                    goForeground("Tap to resume listening", withMic = false, resumeOnTap = true)
                    return
                }
                _serviceState.value = VasuServiceState.LISTENING
                startStateObservers()
            }
        }
    }

    private fun startStateObservers() {
        observerJob?.cancel()
        observerJob = scope.launch {
            // Observe wake word detections
            launch {
                wakeWordListener.detections.collect {
                    Log.i(TAG, "Wake word trigger received")
                    onWakeWordDetected()
                }
            }

            // Observe Gemini Live Voice State
            launch {
                geminiLiveVoiceService.voiceState.collect { liveState ->
                    when (liveState) {
                        GeminiVoiceState.SPEAKING -> {
                            _serviceState.value = VasuServiceState.SPEAKING
                            wakeWordListener.setMutedForPlayback(true)
                            notifyNotification("VASU Speaking...")
                        }
                        GeminiVoiceState.LISTENING -> {
                            _serviceState.value = VasuServiceState.ACTIVE
                            wakeWordListener.pauseForSpeechRecognition()
                            notifyNotification("Listening to you...")
                        }
                        GeminiVoiceState.CONNECTED, GeminiVoiceState.IDLE -> {
                            if (_serviceState.value == VasuServiceState.SPEAKING ||
                                _serviceState.value == VasuServiceState.ACTIVE
                            ) {
                                delay(350)
                                wakeWordListener.setMutedForPlayback(false)
                                wakeWordListener.resumeAfterSpeechRecognition()
                                _serviceState.value = VasuServiceState.LISTENING
                                notifyNotification("Listening for \"Hello Vasu\"")
                            }
                        }
                        GeminiVoiceState.ERROR -> {
                            wakeWordListener.resumeAfterSpeechRecognition()
                            _serviceState.value = VasuServiceState.LISTENING
                        }
                        else -> Unit
                    }
                }
            }

            // Observe AudioSessionManager mic ownership changes
            launch {
                audioSessionManager.currentOwner.collect { owner ->
                    Log.d(TAG, "AudioSessionManager mic owner: ${owner.label}")
                }
            }

            // Observe TTS state — resume listening after TTS finishes
            launch {
                ttsManager.state.collect { ttsState ->
                    if (ttsState == com.vasu.assistant.core.tts.TTSState.IDLE &&
                        _serviceState.value == VasuServiceState.SPEAKING
                    ) {
                        // TTS finished — release mic and go back to listening
                        delay(300) // audio settle time
                        audioSessionManager.releaseAudioFocus()
                        wakeWordListener.setMutedForPlayback(false)
                        wakeWordListener.resumeAfterSpeechRecognition()
                        _serviceState.value = VasuServiceState.LISTENING
                        notifyNotification("Listening for \"Hello Vasu\"")
                    }
                }
            }
        }
    }

    private fun onWakeWordDetected() {
        _serviceState.value = VasuServiceState.ACTIVATING
        notifyNotification("Heard \"Hello Vasu\" - Activating...")

        AssistantOverlayActivity.launch(this)

        scope.launch {
            delay(200) // Brief UI pop transition
            triggerActiveVoiceSession()
        }
    }

    /**
     * Start active voice session after wake word detection.
     * WakeWordDetector has already released mic via AudioSessionManager.
     * Now STT or Gemini Live can acquire it cleanly.
     */
    private fun triggerActiveVoiceSession() {
        _serviceState.value = VasuServiceState.ACTIVE
        wakeWordListener.pauseForSpeechRecognition()

        // Try Gemini Live first — it manages its own mic via NativeMicrophoneRecorder
        val liveStarted = geminiLiveVoiceService.startMicrophoneConversation()
        if (!liveStarted) {
            // Fallback: use STTManager which requests mic from AudioSessionManager
            sttManager.startListening()
        }
    }

    /**
     * Phone call state changes (PhoneStateReceiver). A call owns the mic, so
     * never (re)start the wake-word pipeline from here - the service is in the
     * background during a call, where a microphone foreground service is not
     * allowed (Android 11+ while-in-use, Android 14+ mic FGS prerequisites).
     */
    private fun handleIncomingCall(intent: Intent) {
        when (intent.getStringExtra(EXTRA_CALL_STATE)) {
            "IDLE" -> {
                // Call ended: resume only by user tap (notification -> MainActivity).
                if (settings.wakeWordEnabled.value) {
                    goForeground("Call ended - tap to resume listening", withMic = false, resumeOnTap = true)
                } else {
                    shutdownService()
                }
            }
            else -> {
                // Ringing or call in progress: release the wake-word mic.
                wakeWordListener.stop()
                goForeground("Call in progress - VASU paused", withMic = false)
            }
        }
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    /**
     * Put the service in the foreground.
     *
     * Returns true when foregrounded with mic capture allowed. Never passes a
     * zero type: on Android 14+ that can resolve to the manifest's declared
     * types (microphone|camera|mediaProjection|dataSync), whose runtime
     * prerequisites may not be met, and startForeground would throw.
     */
    private fun goForeground(status: String, withMic: Boolean, resumeOnTap: Boolean = false): Boolean {
        if (!withMic) {
            return try {
                ServiceCompat.startForeground(
                    this, notificationId,
                    buildNotification(status, resumeOnTap),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
                true
            } catch (e: Exception) {
                // No valid foreground call: stop now instead of letting the
                // system raise RemoteServiceException for the missed window.
                Log.e(TAG, "Foreground start failed: ${e.message}")
                stopSelf()
                false
            }
        }
        return try {
            ServiceCompat.startForeground(
                this, notificationId,
                buildNotification(status, resumeOnTap),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Mic foreground service denied: ${e.message}")
            degradeToTapToResume()
            false
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+
            // background-start ban) extends IllegalStateException.
            Log.w(TAG, "Foreground start with mic not allowed: ${e.message}")
            degradeToTapToResume()
            false
        }
    }

    /** Stay foreground without mic and put a tap-to-resume action in front of the user. */
    private fun degradeToTapToResume() {
        try {
            ServiceCompat.startForeground(
                this, notificationId,
                buildNotification("Tap to resume listening", resumeOnTap = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } catch (e: Exception) {
            // Nothing could be foregrounded: stop instead of letting the
            // system raise RemoteServiceException for the missed window.
            Log.e(TAG, "Foreground degrade failed: ${e.message}")
            stopSelf()
        }
    }

    private fun notifyNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, buildNotification(status))
    }

    private fun shutdownService() {
        _serviceState.value = VasuServiceState.STOPPING
        observerJob?.cancel()
        wakeWordListener.stop()
        geminiLiveVoiceService.stopMicrophoneConversation()
        geminiLiveVoiceService.stopSpeaking()
        sttManager.stopListening()
        ttsManager.stop()
        audioSessionManager.forceReleaseMic("service_shutdown")
        // Emergency stop: automation and missions stop too.
        automationEngine.stop()
        missionEngine.cancelAll()
        releaseWakeLock()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        _serviceState.value = VasuServiceState.OFF
        Log.i(TAG, "VasuForegroundService cleanly stopped")
    }

    override fun onDestroy() {
        shutdownService()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(status: String, resumeOnTap: Boolean = false): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            if (resumeOnTap) putExtra(EXTRA_RESUME_WAKE_WORD, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VasuForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VASU Voice Assistant")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(channelId, "VASU Service", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VASU voice assistant background service"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
