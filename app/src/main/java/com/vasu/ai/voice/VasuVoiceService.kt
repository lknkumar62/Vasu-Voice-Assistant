package com.vasu.ai.voice

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
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

class VasuVoiceService : Service(), RecognitionListener, TextToSpeech.OnInitListener {
    private val handler = Handler(Looper.getMainLooper())
    private val runtimePrefs by lazy { getSharedPreferences(PREFS_RUNTIME, MODE_PRIVATE) }
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private lateinit var brain: GeminiAutonomousBrain
    private lateinit var memory: VasuMemoryStore
    private var listening = false
    private var processing = false
    private var ttsReady = false
    private var destroyed = false
    private var serviceGeneration = 0L
    private var deviceLocked = false
    @Volatile private var batteryOptimizationRestricted = false
    private var healthCheckScheduled = false

    private val wakeConfig = VasuWakeWordConfig()
    private var wakeMode = false
    private var wakeBridge: VasuWakeWordAudioBridge? = null
    private var wakeCoordinator: VasuWakeWordCoordinator? = null
    private var wakeRecoveryAttempts = 0
    private var recoveryScheduled = false
    private var recoveryDelayMs = 0L
    private var stressTestEnabled = false
    private var stressTestFailureCount = 0
    private var stressTestRecoveryCount = 0
    private lateinit var commandTimeoutController: VasuCommandListeningTimeoutController
    private var listeningRequireWakeWord = true

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (destroyed) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_UNLOCKED -> {
                    updateDeviceLockState()
                    if (!wakeMode) return
                    if (intent.action == Intent.ACTION_SCREEN_ON || intent.action == Intent.ACTION_USER_UNLOCKED) {
                        ensureWakeWordModeRunning()
                    } else if (hasHealthyWakeStack()) {
                        updateBackgroundReliabilityNotification()
                    }
                }
            }
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean = !destroyed && generation == serviceGeneration

    private fun resetRecoveryState() {
        wakeRecoveryAttempts = 0
        recoveryDelayMs = 0L
        recoveryScheduled = false
        if (::commandTimeoutController.isInitialized) commandTimeoutController.stop()
    }

    private fun isRecoverableWakeFailure(reason: String): Boolean = when (reason) {
        "speech_timeout", "recognizer_busy", "speech_error", "recognizer_start_error", "audio_failure", "recovery_failed" -> true
        "permission_error", "recognizer_unavailable" -> false
        else -> true
    }

    private fun calculateRecoveryDelay(): Long {
        val base = wakeConfig.recoveryDelayMs.coerceAtLeast(100L)
        val multiplier = when (wakeRecoveryAttempts) { 0 -> 1L; 1 -> 2L; 2 -> 4L; else -> 8L }
        return (base * multiplier).coerceAtMost(15_000L)
    }

    private fun updateDeviceLockState() {
        deviceLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
    }

    private fun updateBatteryOptimizationState() {
        batteryOptimizationRestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) != true
        } else false
    }

    private fun persistWakeRuntimeStatus(healthy: Boolean, failure: String? = null) {
        if (destroyed) return
        runtimePrefs.edit().apply {
            putBoolean(KEY_WAKE_HEALTH, healthy)
            if (failure != null) putString(KEY_LAST_FAILURE, failure)
        }.apply()
    }

    private fun persistStressTestSummary() {
        if (!stressTestEnabled && stressTestFailureCount == 0 && stressTestRecoveryCount == 0) return
        runtimePrefs.edit()
            .putInt(KEY_STRESS_TEST_FAILURES, stressTestFailureCount)
            .putInt(KEY_STRESS_TEST_RECOVERIES, stressTestRecoveryCount)
            .apply()
    }

    private fun logStressTestSnapshot() {
        println(
            "VASU_STRESS_SNAPSHOT " +
                "enabled=$stressTestEnabled " +
                "failures=$stressTestFailureCount " +
                "recoveries=$stressTestRecoveryCount " +
                "generation=$serviceGeneration " +
                "wakeMode=$wakeMode " +
                "listening=$listening " +
                "processing=$processing " +
                "locked=$deviceLocked " +
                "batteryRestricted=$batteryOptimizationRestricted"
        )
    }

    private fun enableStressTestingForRuntime() {
        stressTestEnabled = true
        stressTestFailureCount = 0
        stressTestRecoveryCount = 0
        println("VASU_STRESS_TEST_ENABLED")
    }

    private fun disableStressTestingForRuntime() {
        stressTestEnabled = false
        println(
            "VASU_STRESS_TEST_DISABLED " +
                "failures=$stressTestFailureCount " +
                "recoveries=$stressTestRecoveryCount"
        )
    }

    private fun handleStressTestIntent(intent: Intent?) {
        if (destroyed) return

        val testCase = intent?.getStringExtra(EXTRA_STRESS_TEST_CASE) ?: STRESS_TEST_DISABLED
        if (!stressTestEnabled && testCase != STRESS_TEST_DISABLED) {
            println("VASU_STRESS_TEST_DISABLED")
            return
        }

        when (testCase) {
            STRESS_TEST_AUDIO_FAILURE -> {
                stressTestFailureCount++
                println("VASU_STRESS_AUDIO_FAILURE count=$stressTestFailureCount")
                finishWakeCommand("audio_failure")
            }
            STRESS_TEST_RECOGNIZER_BUSY -> {
                stressTestFailureCount++
                println("VASU_STRESS_RECOGNIZER_BUSY count=$stressTestFailureCount")
                finishWakeCommand("recognizer_busy")
            }
            STRESS_TEST_TIMEOUT -> {
                stressTestFailureCount++
                println("VASU_STRESS_TIMEOUT count=$stressTestFailureCount")
                finishWakeCommand("speech_timeout")
            }
            STRESS_TEST_RECOVERY -> {
                stressTestRecoveryCount++
                println("VASU_STRESS_RECOVERY count=$stressTestRecoveryCount")
                recoverWakeWordMode()
            }
            STRESS_TEST_SAFE_STOP -> {
                println("VASU_STRESS_SAFE_STOP")
                safeStopWakeWord("stress_test")
            }
            STRESS_TEST_DISABLED -> disableStressTestingForRuntime()
        }

        logStressTestSnapshot()
    }

    private fun logVoiceLifecycle(event: String) {
        println("VASU_VOICE_LIFECYCLE event=$event generation=$serviceGeneration wakeMode=$wakeMode locked=$deviceLocked batteryRestricted=$batteryOptimizationRestricted")
    }

    private fun updateWakeWordNotification() {
        updateNotification(if (deviceLocked) "Listening for Hello Vasu — screen locked" else "Listening for Hello Vasu")
    }

    private fun updateBackgroundReliabilityNotification() {
        if (destroyed) return
        updateBatteryOptimizationState()
        if (!wakeMode) { updateNotification("VASU ready"); return }
        when {
            !hasHealthyWakeStack() -> updateNotification("Wake word recovering")
            batteryOptimizationRestricted && deviceLocked -> updateNotification("Listening for Hello Vasu — battery optimization enabled")
            deviceLocked -> updateNotification("Listening for Hello Vasu — screen locked")
            batteryOptimizationRestricted -> updateNotification("Listening for Hello Vasu — battery optimization enabled")
            else -> updateNotification("Listening for Hello Vasu")
        }
    }

    private fun hasHealthyWakeStack(): Boolean = wakeCoordinator?.let { wakeMode && it.isHealthy() } == true

    private fun safeStopWakeWord(reason: String) {
        logVoiceLifecycle("safe_stop:$reason")
        persistWakeRuntimeStatus(false, reason)
        recoveryScheduled = false
        healthCheckScheduled = false
        if (::commandTimeoutController.isInitialized) commandTimeoutController.stop()
        stopListening()
        wakeCoordinator?.stop(); wakeCoordinator = null
        wakeBridge?.let { runCatching { it.release() } }; wakeBridge = null
        wakeMode = false; processing = false; listening = false
        resetRecoveryState()
        updateNotification("Wake word stopped")
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { startActivity(intent) }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        }
    }

    private fun scheduleWakeHealthCheck() {
        if (destroyed || healthCheckScheduled) return
        healthCheckScheduled = true
        val generation = serviceGeneration
        handler.postDelayed({
            healthCheckScheduled = false
            if (!isCurrentGeneration(generation) || !wakeMode || !wakeConfig.enabled) return@postDelayed
            updateDeviceLockState(); updateBatteryOptimizationState()
            if (!hasHealthyWakeStack()) ensureWakeWordModeRunning() else updateBackgroundReliabilityNotification()
            scheduleWakeHealthCheck()
        }, HEALTH_CHECK_INTERVAL_MS)
    }

    override fun onCreate() {
        super.onCreate(); destroyed = false; serviceGeneration++
        updateDeviceLockState(); updateBatteryOptimizationState(); healthCheckScheduled = false
        wakeMode = false; listening = false; processing = false; wakeBridge = null; wakeCoordinator = null
        recoveryScheduled = false; wakeRecoveryAttempts = 0
        stressTestEnabled = false; stressTestFailureCount = 0; stressTestRecoveryCount = 0
        val desiredMode = runtimePrefs.getString(KEY_DESIRED_MODE, if (wakeConfig.enabled) MODE_WAKE_WORD else MODE_MANUAL)
        brain = GeminiAutonomousBrain(this); memory = VasuMemoryStore(this); tts = TextToSpeech(this, this)
        commandTimeoutController = VasuCommandListeningTimeoutController(wakeConfig.commandTimeoutMs) { finishWakeCommand("timeout") }
        resetRecoveryState()
        runtimePrefs.edit().putBoolean(KEY_VOICE_RUNNING, true).putLong(KEY_VOICE_STARTED_AT, System.currentTimeMillis()).apply()
        createChannel()
        runCatching { registerReceiver(screenStateReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_UNLOCKED) }) }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(if (wakeConfig.enabled && deviceLocked) "Wake word: Hello Vasu — screen locked" else "Wake word: Hello Vasu"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        logVoiceLifecycle("created")
        when {
            desiredMode == MODE_WAKE_WORD && wakeConfig.enabled -> startWakeWordMode()
            desiredMode == MODE_MANUAL && SpeechRecognizer.isRecognitionAvailable(this) -> { ensureRecognizer(); startListeningSoon(500, false) }
            wakeConfig.enabled -> { runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_WAKE_WORD).apply(); startWakeWordMode() }
            SpeechRecognizer.isRecognitionAvailable(this) -> { runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_MANUAL).apply(); ensureRecognizer(); startListeningSoon(500, true) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (destroyed) return START_NOT_STICKY
        updateDeviceLockState(); updateBatteryOptimizationState()
        when (intent?.action) {
            ACTION_MANUAL_COMMAND -> { runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_MANUAL).apply(); startManualCommandListening() }
            ACTION_WAKE_WORD_MODE -> { runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_WAKE_WORD).apply(); ensureWakeWordModeRunning() }
            ACTION_BATTERY_SETTINGS -> openBatterySettings()
            ACTION_STRESS_TEST -> handleStressTestIntent(intent)
            null -> if (wakeConfig.enabled) ensureWakeWordModeRunning() else { ensureRecognizer(); if (!listening && !processing) startListeningSoon(200, true) }
            else -> if (wakeConfig.enabled) ensureWakeWordModeRunning()
        }
        if (wakeMode) updateBackgroundReliabilityNotification()
        return START_STICKY
    }

    private fun ensureWakeWordModeRunning() {
        if (destroyed || !wakeConfig.enabled) return
        updateDeviceLockState(); updateBatteryOptimizationState()
        if (hasHealthyWakeStack()) { updateBackgroundReliabilityNotification(); return }
        startWakeWordMode()
    }

    private fun ensureRecognizer(): Boolean {
        if (destroyed) return false
        if (recognizer != null) return true
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return false
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
        return true
    }

    private fun startManualCommandListening() {
        runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_MANUAL).apply()
        wakeMode = false; healthCheckScheduled = false; resetRecoveryState()
        wakeCoordinator?.stop(); wakeCoordinator = null; wakeBridge?.release(); wakeBridge = null
        ensureRecognizer(); startListeningSoon(200, false)
    }

    private fun startWakeWordMode() {
        if (destroyed || !wakeConfig.enabled) return
        updateDeviceLockState(); updateBatteryOptimizationState()
        if (hasHealthyWakeStack()) { updateBackgroundReliabilityNotification(); scheduleWakeHealthCheck(); return }
        runtimePrefs.edit().putString(KEY_DESIRED_MODE, MODE_WAKE_WORD).apply()
        wakeMode = true; commandTimeoutController.stop(); stopListening()
        val readiness = VasuWakeWordReadinessChecker(this, wakeConfig).check()
        if (readiness != VasuWakeWordReadiness.READY) { persistWakeRuntimeStatus(false, "readiness_$readiness"); updateNotification("Wake word unavailable"); return }
        wakeBridge?.let { runCatching { it.release() } }; wakeBridge = null; wakeCoordinator?.stop(); wakeCoordinator = null
        val keyStore = VasuWakeWordKeyStore(this); val manager = VasuWakeWordManager(wakeConfig)
        val audioCapture = VasuAudioCaptureManager(this, VasuAudioCaptureConfig()); val audioLifecycleManager = VasuAudioLifecycleManager(wakeConfig)
        val coordinator = VasuWakeWordCoordinator(manager, audioLifecycleManager, audioCapture); wakeCoordinator = coordinator
        val detector = VasuPorcupineWakeWordDetector(this, wakeConfig, { keyStore.getAccessKey() }, onDetected = { })
        val bridge = VasuWakeWordAudioBridge(audioCapture, detector, coordinator) {
            val generation = serviceGeneration; handler.post { if (isCurrentGeneration(generation)) startCommandListeningFromWake() }
        }
        wakeBridge = bridge
        if (!coordinator.start()) { persistWakeRuntimeStatus(false, "coordinator_start_failed"); coordinator.stop(); wakeBridge = null; wakeCoordinator = null; updateNotification("Wake word unavailable"); return }
        if (!bridge.start()) { persistWakeRuntimeStatus(false, "bridge_start_failed"); coordinator.onAudioFailure(); coordinator.stop(); wakeBridge = null; wakeCoordinator = null; updateNotification("Wake word unavailable"); return }
        resetRecoveryState(); persistWakeRuntimeStatus(true); runtimePrefs.edit().putLong(KEY_LAST_RECOVERY, System.currentTimeMillis()).apply()
        logVoiceLifecycle("wake_started"); updateBackgroundReliabilityNotification(); scheduleWakeHealthCheck()
    }

    private fun startCommandListeningFromWake() {
        if (!wakeMode || destroyed) return
        commandTimeoutController.start()
        if (!ensureRecognizer()) { finishWakeCommand("recognizer_unavailable"); return }
        startListeningSoon(150, false); updateNotification("Listening for command")
    }

    private fun startListeningSoon(delay: Long, requireWakeWord: Boolean) {
        if (destroyed) return
        listeningRequireWakeWord = requireWakeWord; val generation = serviceGeneration
        handler.postDelayed({ if (isCurrentGeneration(generation)) startListening() }, delay.coerceAtLeast(0L))
    }

    private fun startListening() {
        if (destroyed || recognizer == null || listening || processing) return
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3); putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        listening = true
        try { recognizer?.startListening(request) } catch (_: Exception) { listening = false; if (wakeMode) finishWakeCommand("recognizer_start_error") else startListeningSoon(1000, listeningRequireWakeWord) }
    }

    private fun stopListening() { listening = false; runCatching { recognizer?.cancel() } }

    private fun handleTranscript(raw: String) {
        if (processing) return
        val text = raw.trim(); if (text.isBlank()) return
        val command = if (!listeningRequireWakeWord) text else {
            val lower = text.lowercase(Locale.ROOT); val variants = listOf("hello vasu", "hey vasu", "helo vasu", "हेलो वासु", "हैलो वासु")
            val wake = variants.firstOrNull { lower.contains(it) } ?: return
            text.substring(wake.length.coerceAtMost(text.length)).trim()
        }
        if (command.isBlank()) { speak("Haan Boss, boliye."); if (wakeMode) finishWakeCommand("empty_command"); return }
        processing = true; commandTimeoutController.stop(); stopListening(); updateNotification("Processing command")
        val generation = serviceGeneration
        brain.handleAsync(command) { result ->
            handler.post {
                if (!isCurrentGeneration(generation)) return@post
                memory.add(command, result.reply, result.handled, result.usedGemini)
                updateNotification(if (result.handled) "Ready — Hello Vasu" else "Command failed — Hello Vasu")
                val response = result.reply.ifBlank { if (result.handled) "Ho gaya Boss." else "Command execute nahi hua." }
                speak(response); processing = false
                if (wakeMode) { resetRecoveryState(); val callbackGeneration = serviceGeneration; handler.postDelayed({ if (isCurrentGeneration(callbackGeneration)) startWakeWordMode() }, wakeConfig.recoveryDelayMs.coerceAtLeast(0L)) }
                else startListeningSoon(800, true)
            }
        }
    }

    private fun finishWakeCommand(reason: String) {
        if (!wakeMode || destroyed) return
        commandTimeoutController.stop(); stopListening(); processing = false
        persistWakeRuntimeStatus(false, reason)
        if (!isRecoverableWakeFailure(reason)) { safeStopWakeWord(reason); return }
        if (recoveryScheduled) return
        val coordinator = wakeCoordinator ?: run { safeStopWakeWord("coordinator_missing"); return }
        if (!coordinator.onAudioFailure()) { safeStopWakeWord("recovery_exhausted"); return }
        wakeRecoveryAttempts++; recoveryDelayMs = calculateRecoveryDelay(); recoveryScheduled = true
        logVoiceLifecycle("wake_recovery"); updateNotification("Recovering wake word")
        val generation = serviceGeneration
        handler.postDelayed({ if (!isCurrentGeneration(generation)) return@postDelayed; recoveryScheduled = false; if (!wakeMode) return@postDelayed; recoverWakeWordMode() }, recoveryDelayMs)
    }

    private fun recoverWakeWordMode() {
        if (destroyed || !wakeMode) return
        val coordinator = wakeCoordinator ?: run { recoveryScheduled = false; startWakeWordMode(); return }
        if (coordinator.completeAudioRecovery()) {
            recoveryScheduled = false; persistWakeRuntimeStatus(true); runtimePrefs.edit().putLong(KEY_LAST_RECOVERY, System.currentTimeMillis()).apply()
            resetRecoveryState(); updateBackgroundReliabilityNotification(); scheduleWakeHealthCheck(); return
        }
        println("VASU_AUDIO_RECOVERY_RETRY_FAILED")
        if (!coordinator.onAudioFailure()) { safeStopWakeWord("recovery_failed"); return }
        wakeRecoveryAttempts++; recoveryDelayMs = calculateRecoveryDelay(); recoveryScheduled = true
        val generation = serviceGeneration
        handler.postDelayed({ if (!isCurrentGeneration(generation)) return@postDelayed; recoveryScheduled = false; if (!wakeMode) return@postDelayed; recoverWakeWordMode() }, recoveryDelayMs)
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        stopListening(); tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "vasu-${System.currentTimeMillis()}")
        val generation = serviceGeneration
        handler.postDelayed({ if (isCurrentGeneration(generation) && !processing && !wakeMode) startListeningSoon(500, true) }, (text.length * 55L).coerceIn(700, 5000))
    }

    private fun updateNotification(text: String) { getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text)) }

    private fun buildNotification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification).setContentTitle("VASU AI").setContentText(text).setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE).setPriority(NotificationCompat.PRIORITY_LOW).build()

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "VASU Voice Assistant", NotificationManager.IMPORTANCE_LOW).apply { description = "VASU foreground voice service" })
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() { listening = false; if (wakeMode) { if (!processing && commandTimeoutController.isRunning()) startListeningSoon(150, false) } else if (!processing) startListeningSoon(300, listeningRequireWakeWord) }

    override fun onError(error: Int) {
        listening = false; if (processing) return
        if (wakeMode) when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finishWakeCommand("speech_timeout")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> finishWakeCommand("recognizer_busy")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> finishWakeCommand("permission_error")
            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_NETWORK -> finishWakeCommand("speech_error")
            else -> finishWakeCommand("speech_error")
        } else startListeningSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 500, listeningRequireWakeWord)
    }

    override fun onResults(results: Bundle?) { listening = false; if (processing) return; results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::handleTranscript); if (!processing && !wakeMode) startListeningSoon(250, listeningRequireWakeWord) }
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
    override fun onInit(status: Int) { ttsReady = status == TextToSpeech.SUCCESS; if (ttsReady) { tts?.language = Locale("hi", "IN"); tts?.setSpeechRate(0.95f) } }

    override fun onDestroy() {
        logVoiceLifecycle("destroyed")
        persistStressTestSummary()
        stressTestEnabled = false
        stressTestFailureCount = 0
        stressTestRecoveryCount = 0
        destroyed = true; serviceGeneration++; recoveryScheduled = false; healthCheckScheduled = false
        wakeMode = false; listening = false; processing = false
        handler.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenStateReceiver) }
        if (::commandTimeoutController.isInitialized) commandTimeoutController.stop()
        wakeCoordinator?.stop(); wakeCoordinator = null
        wakeBridge?.let { runCatching { it.release() } }; wakeBridge = null
        stopListening(); recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null; ttsReady = false
        resetRecoveryState(); runtimePrefs.edit().putBoolean(KEY_VOICE_RUNNING, false).apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val CHANNEL_ID = "vasu_voice"
        const val NOTIFICATION_ID = 1001
        const val ACTION_MANUAL_COMMAND = "com.vasu.ai.voice.MANUAL_COMMAND"
        const val ACTION_WAKE_WORD_MODE = "com.vasu.ai.voice.WAKE_WORD_MODE"
        const val ACTION_BATTERY_SETTINGS = "com.vasu.ai.voice.BATTERY_SETTINGS"
        const val ACTION_STRESS_TEST = "com.vasu.ai.voice.STRESS_TEST"
        const val EXTRA_STRESS_TEST_CASE = "stress_test_case"
        const val STRESS_TEST_DISABLED = "disabled"
        const val STRESS_TEST_AUDIO_FAILURE = "audio_failure"
        const val STRESS_TEST_RECOGNIZER_BUSY = "recognizer_busy"
        const val STRESS_TEST_TIMEOUT = "timeout"
        const val STRESS_TEST_RECOVERY = "recovery"
        const val STRESS_TEST_SAFE_STOP = "safe_stop"
        const val PREFS_RUNTIME = "vasu_runtime"
        const val KEY_VOICE_RUNNING = "voice_running"
        const val KEY_VOICE_STARTED_AT = "voice_started_at"
        const val KEY_DESIRED_MODE = "desired_mode"
        const val KEY_WAKE_HEALTH = "wake_health"
        const val KEY_LAST_FAILURE = "last_voice_failure"
        const val KEY_LAST_RECOVERY = "last_voice_recovery"
        const val KEY_STRESS_TEST_FAILURES = "stress_test_failures"
        const val KEY_STRESS_TEST_RECOVERIES = "stress_test_recoveries"
        const val MODE_WAKE_WORD = "wake_word"
        const val MODE_MANUAL = "manual"
        const val HEALTH_CHECK_INTERVAL_MS = 30_000L
    }
}
