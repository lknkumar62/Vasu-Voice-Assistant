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

    override fun onCreate() {
        super.onCreate()
        brain = GeminiAutonomousBrain(this)
        memory = VasuMemoryStore(this)
        tts = TextToSpeech(this, this)
        getSharedPreferences("vasu_runtime", MODE_PRIVATE).edit().putBoolean("voice_running", true).apply()
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification("Wake word: Hello Vasu"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(this) }
            startListeningSoon(500)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startListeningSoon(200)
        return START_STICKY
    }

    private fun startListeningSoon(delay: Long) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startListening() }, delay)
    }

    private fun startListening() {
        if (isDestroyed || recognizer == null || listening || processing) return
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
            startListeningSoon(1000)
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
        val lower = text.lowercase(Locale.ROOT)
        val wakeVariants = listOf("hello vasu", "hey vasu", "helo vasu", "हेलो वासु", "हैलो वासु")
        val wake = wakeVariants.firstOrNull { lower.contains(it) } ?: return
        val command = text.substringAfter(wake, "", ignoreCase = true).trim()
        if (command.isBlank()) {
            speak("Haan Boss, boliye.")
            return
        }

        processing = true
        stopListening()
        updateNotification("Processing: $command")
        brain.handleAsync(command) { result ->
            memory.add(command, result.reply, result.handled, result.usedGemini)
            updateNotification(if (result.handled) "Ready — Hello Vasu" else "Command failed — Hello Vasu")
            val response = result.reply.ifBlank { if (result.handled) "Ho gaya Boss." else "Command execute nahi hua." }
            speak(response)
            processing = false
            startListeningSoon(800)
        }
    }

    private fun speak(text: String) {
        if (!ttsReady) return
        stopListening()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "vasu-${System.currentTimeMillis()}")
        handler.postDelayed({ if (!processing) startListeningSoon(500) }, (text.length * 55L).coerceIn(700, 5000))
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
        if (!processing) startListeningSoon(300)
    }
    override fun onError(error: Int) {
        listening = false
        if (!processing) startListeningSoon(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 1500 else 500)
    }
    override fun onResults(results: Bundle?) {
        listening = false
        if (processing) return
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(::handleTranscript)
        if (!processing) startListeningSoon(250)
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
        handler.removeCallbacksAndMessages(null)
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
    }
}
