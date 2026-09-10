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
        const val EXTRA_STATUS = "status"

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
            context.startService(Intent(context, VasuForegroundService::class.java).apply { action = ACTION_STOP })
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
            context.startService(Intent(context, VasuForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_STATUS, status)
            })
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
                acquire(12 * 60 * 60 * 1000L) // 12 hours max
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
            else -> {
                beginWakeWordPipeline()
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

        goForeground("VASU active - Listening for \"Hello Vasu\"", withMic = true)
        wakeWordListener.initialize()

        when (wakeWordListener.state.value) {
            WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR -> {
                _serviceState.value = VasuServiceState.ERROR
                notifyNotification(wakeWordListener.unavailableReason.value ?: "Wake word unavailable")
            }
            else -> {
                wakeWordListener.start()
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
                    Log.i(TAG, "Wake word trigger received in VasuForegroundService")
                    onWakeWordDetected()
                }
            }

            // Observe Gemini Live Voice State
            launch {
                geminiLiveVoiceService.voiceState.collect { liveState ->
                    when (liveState) {
                        GeminiVoiceState.SPEAKING -> {
                            _serviceState.value = VasuServiceState.SPEAKING
                            // Echo suppression: mute wake-word while speaker is outputting audio
                            wakeWordListener.setMutedForPlayback(true)
                            notifyNotification("VASU Speaking (Kore)...")
                        }
                        GeminiVoiceState.LISTENING -> {
                            _serviceState.value = VasuServiceState.ACTIVE
                            wakeWordListener.pauseForSpeechRecognition()
                            notifyNotification("Listening to you...")
                        }
                        GeminiVoiceState.CONNECTED, GeminiVoiceState.IDLE -> {
                            if (_serviceState.value == VasuServiceState.SPEAKING || _serviceState.value == VasuServiceState.ACTIVE) {
                                // Speaker finished - allow audio to settle before unmuting wake word
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
        }
    }

    private fun onWakeWordDetected() {
        _serviceState.value = VasuServiceState.ACTIVATING
        notifyNotification("Heard \"Hello Vasu\" - Activating...")

        // Launch UI overlay for immediate visual and interactive feedback
        AssistantOverlayActivity.launch(this)

        scope.launch {
            delay(200) // Brief UI pop transition
            triggerActiveVoiceSession()
        }
    }

    private fun triggerActiveVoiceSession() {
        _serviceState.value = VasuServiceState.ACTIVE
        wakeWordListener.pauseForSpeechRecognition()

        // Start real-time microphone stream with Gemini Live
        val liveStarted = geminiLiveVoiceService.startMicrophoneConversation()
        if (!liveStarted) {
            // If Gemini Live connection fails, use local STT fallback
            sttManager.startListening()
        }
    }

    private fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun goForeground(status: String, withMic: Boolean) {
        ServiceCompat.startForeground(
            this,
            notificationId,
            buildNotification(status),
            if (withMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            else 0
        )
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

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
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
