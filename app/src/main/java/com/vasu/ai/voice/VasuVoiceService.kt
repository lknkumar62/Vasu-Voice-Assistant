package com.vasu.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.vasu.ai.R
import com.vasu.ai.core.GeminiAutonomousBrain
import com.vasu.ai.core.VasuAudioCaptureConfig
import com.vasu.ai.core.VasuAudioCaptureManager
import com.vasu.ai.core.VasuAudioLifecycleManager
import com.vasu.ai.core.VasuCommandListeningTimeoutController
import com.vasu.ai.core.VasuPorcupineWakeWordDetector
import com.vasu.ai.core.VasuWakeWordAudioBridge
import com.vasu.ai.core.VasuWakeWordConfig
import com.vasu.ai.core.VasuWakeWordCoordinator
import com.vasu.ai.core.VasuWakeWordKeyStore
import com.vasu.ai.core.VasuWakeWordManager
import com.vasu.ai.core.VasuWakeWordReadiness
import com.vasu.ai.core.VasuWakeWordReadinessChecker
import com.vasu.ai.memory.VasuMemoryStore
import java.util.Locale

/** Foreground voice loop. Android still controls microphone/background availability. */
class VasuVoiceService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var brain: GeminiAutonomousBrain
    private lateinit var memory: VasuMemoryStore
    private var listening = false
    private var processing = false
    private var ttsReady = false
    private var destroyed = false

    private val wakeConfig = VasuWakeWordConfig()
    private var wakeMode = false
    private var wakeBridge: VasuWakeWordAudioBridge? = null
    private var wakeCoordinator: VasuWakeWordCoordinator? = null
    private var wakeRecoveryAttempts = 0
    private lateinit var commandTimeoutController: VasuCommandListeningTimeoutController

    override fun onCreate() {
        super.onCreate()
        destroyed = false
        brain = GeminiAutonomousBrain(this)
        memory = VasuMemoryStore(this)
        tts = TextToSpeech(this, this)
        commandTimeoutController = VasuCommandListeningTimeoutController(
            wakeConfig.commandTimeoutMs
        ) {
            finishWakeCommand("timeout")
        }
        getSharedPreferences("vasu_runtime", MODE_PRIVATE).edit().putBoolean("voice_running", true).apply()
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification("Wake word: Hello Vasu"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )

        if (wakeConfig.enabled) {
            startWakeWordMode()
        } else if (SpeechRecognizer.isRecognitionAvailable(this)) {
            ensureRecognizer()
            startListeningSoon(500, requireWakeWord = true)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_MANUAL_COMMAND -> startManualCommandListening()
            ACTION_WAKE_WORD_MODE -> startWakeWordMode()
            else -> if (!wakeConfig.enabled) startListeningSoon(200, requireWakeWord = true)
        }
        return START_STICKY
    }

    private fun ensureRecognizer(): Boolean {
        if (destroyed) return false
        if (recognizer != null) return true
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return false
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also {
            it.setRecognitionListener(this)
        }
        return true
    }

    private fun startManualCommandListening() {
        wakeMode = false
        wakeRecoveryAttempts = 0
        commandTimeoutController.stop()
        wakeBridge?.stop()
        wakeBridge = null
        ensureRecognizer()
        startListeningSoon(200, requireWakeWord = false)
    }

    private fun startWakeWordMode() {
        if (destroyed || !wakeConfig.enabled) return
        wakeMode = true
        commandTimeoutController.stop()
        stopListening()

        val readiness = VasuWakeWordReadinessChecker(this, wakeConfig).check()
        if (readiness != VasuWakeWordReadiness.READY) {
            updateNotification("Wake word unavailable")
            return
        }

        wakeBridge?.release()
        val keyStore = VasuWakeWordKeyStore(this)
        val manager = VasuWakeWordManager(wakeConfig)
        val coordinator = VasuWakeWordCoordinator(
            manager,
            VasuAudioLifecycleManager(wakeConfig)
        )
        wakeCoordinator = coordinator

        val detector = VasuPorcupineWakeWordDetector(
            context = this,
            config = wakeConfig,
            accessKeyProvider = { keyStore.getAccessKey() },
            onDetected = { }
        )

        val bridge = VasuWakeWordAudioBridge(
            audioCapture = VasuAudioCaptureManager(this, VasuAudioCaptureConfig()),
            detector = detector,
            coordinator = coordinator,
            onCommandListeningRequested = {
                handler.post { startCommandListeningFromWake() }
            }
        )
        wakeBridge = bridge

        manager.start()
        if (!bridge.start()) {
            manager.stop()
            updateNotification("Wake word unavailable")
            return
        }
        wakeRecoveryAttempts = 0
        updateNotification("Listening for Hello Vasu")
    }

    private fun startCommandListeningFromWake() {
        if (!wakeMode || destroyed) return
        commandTimeoutController.start()
        if (!ensureRecognizer()) {
            finishWakeCommand("recognizer_unavailable")
            return
        }
        startListeningSoon(150, requireWakeWord = false)
        updateNotification("Listening for command")
    }

    private fun startListeningSoon(delay: Long, requireWakeWord: Boolean) {
        handler.removeCallbacks(LISTENING_CALLBACK)
        LISTENING_REQUIRE_WAKE_WORD = requireWakeWord
        handler.postDelayed(LISTENING_CALLBACK, delay.coerceAtLeast(0L))
    }

    private val LISTENING_CALLBACK = Runnable { startListening() }
    private var LISTENING_REQUIRE_WAKE_WORD = true

    private fun startListening() {
        if (destroyed || recognizer == null || listening || processing) return
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        listening = true
        try {
            recognizer?.startListening(request)
        } catch (_: Exception) {
            listening = false
            if (wakeMode) finishWakeCommand("recognizer_start_error")
            else startListeningSoon(1000, LISTENING_REQUIRE_WAKE_WORD)
        }
    }

    private fun stopListening() {
        listening = false
        runCatching { recognizer?.cancel() }
    }

    private fun handleTranscript(raw: String) {
        if (processing) return
        val text = raw.trim()
        if (text.isBlank()) return

        val command = if (!LISTENING_REQUIRE_WAKE_WORD) {
            text
        } else {
            val lower = text.lowercase(Locale.ROOT)
            val wakeVariants = listOf("hello vasu", "hey vasu", "helo vasu", "हेलो वासु", "हैलो वासु")
            val wake = wakeVariants.firstOrNull { lower.contains(it) } ?: return
            text.substring(wake.length.coerceAtMost(text.length)).trim()
        }

        if (command.isBlank()) {
            speak("Haan Boss, boliye.")
            if (wakeMode) finishWakeCommand("empty_command")
            return
        }

        processing = true
        commandTimeoutController.stop()
        stopListening()
        updateNotification("Processing command")
        brain.handleAsync(command) { result ->
            memory.add(command, result.reply, result.handled, result.usedGemini)
            updateNotification(if (result.handled) "Ready — Hello Vasu" else "Command failed — Hello Vasu")
            val response = result.reply.ifBlank { if (result.handled) "Ho gaya Boss." else "Command execute nahi hua." }
            speak(response)
            processing = false
            if (wakeMode) {
                wakeRecoveryAttempts = 0
                handler.postDelayed({ startWakeWordMode() }, wakeConfig.recoveryDelayMs.coerceAtLeast(0L))
            } else {
                startListeningSoon(800, requireWakeWord = true)
            }
        }
    }

    private fun finishWakeCommand(reason: String) {
        if (!wakeMode || destroyed) return
        commandTimeoutController.stop()
        stopListening()
        processing = false
        wakeRecoveryAttempts++
        println("VASU_COMMAND_LISTENING_STOPPED reason=$reason")

        if (wakeRecoveryAttempts >= wakeConfig.maxRecoveryAttempts) {
            println("VASU_AUDIO_RECOVERY_FAILED")
            println("VASU_WAKEWORD_SAFE_STOP")
            wakeBridge?.stop()
            wakeBridge = null
            wakeMode = false
            return
        }

        println("VASU_AUDIO_RECOVERY attempt=$wakeRecoveryAttempts")
        handler.postDelayed({ startWakeWordMode() }, wakeConfig.recoveryDelayMs.coerceAtLeast(0L))
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        stopListening()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "vasu-${System.currentTimeMillis()}")
        handler.postDelayed({
            if (!processing && !wakeMode) startListeningSoon(500, requireWakeWord = true)
        }, (text.length * 55L).coerceIn(700, 5000))
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("VASU AI")
        .setContentText(text)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "VASU Voice Assistant", NotificationManager.IMPORTANCE_LOW).apply {
            description = "VASU foreground voice service"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        listening = false
        if (wakeMode) {
            if (!processing && commandTimeoutController.isRunning()) startListeningSoon(150, requireWakeWord = false)
        } else if (!processing) {
            startListeningSoon(300, LISTENING_REQUIRE_WAKE_WORD)
        }
    }

    override fun onError(error: Int) {
        listening = false
        if (processing) return
        if (wakeMode) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finishWakeCommand("speech_timeout")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> finishWakeCommand("recognizer_busy")
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> finishWakeCommand("permission_error")
                SpeechRecognizer.ERROR_AUDIO,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_NETWORK -> finishWakeCommand("speech_error")
                else -> finishWakeCommand("speech_error")
            }
        } else {
            startListeningSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 500, LISTENING_REQUIRE_WAKE_WORD)
        }
    }

    override fun onResults(results: Bundle?) {
        listening = false
        if (processing) return
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.let(::handleTranscript)
        if (!processing && !wakeMode) startListeningSoon(250, LISTENING_REQUIRE_WAKE_WORD)
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("hi", "IN")
            tts?.setSpeechRate(0.95f)
        }
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)
        commandTimeoutController.stop()
        wakeBridge?.release()
        wakeBridge = null
        stopListening()
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        getSharedPreferences("vasu_runtime", MODE_PRIVATE).edit().putBoolean("voice_running", false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "vasu_voice"
        const val NOTIFICATION_ID = 1001
        const val ACTION_MANUAL_COMMAND = "com.vasu.ai.voice.MANUAL_COMMAND"
        const val ACTION_WAKE_WORD_MODE = "com.vasu.ai.voice.WAKE_WORD_MODE"
    }
}
