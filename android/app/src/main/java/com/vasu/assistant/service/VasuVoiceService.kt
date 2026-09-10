package com.vasu.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.devicecontrol.DeviceControlManager
import com.vasu.assistant.voice.GeminiLiveSpeechEngine

/**
 * Foreground Service for VASU Voice Assistant.
 * Maintains microphone listening, background wake lock, and audio focus.
 */
class VasuVoiceService : Service() {

    private val tag = "VasuVoiceService"
    private val channelId = "vasu_voice_service_channel"
    private val notificationId = 1001

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechEngine: GeminiLiveSpeechEngine? = null
    private var deviceControl: DeviceControlManager? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "VasuVoiceService onCreate")

        deviceControl = DeviceControlManager(this)
        speechEngine = GeminiLiveSpeechEngine(this, "")

        acquireWakeLock()
        createNotificationChannel()
        startForeground(notificationId, buildNotification("VASU is active and listening..."))
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "VasuAssistant:VoiceWakeLock"
            )?.apply {
                acquire(10 * 60 * 1000L /* 10 minutes */)
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire wake lock", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "VASU Assistant Active Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps VASU voice assistant active in the background"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("VASU Assistant")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val apiKey = intent?.getStringExtra("API_KEY") ?: ""
        if (apiKey.isNotEmpty()) {
            speechEngine?.setApiKey(apiKey)
        }

        if (action == "ACTION_STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "VasuVoiceService onDestroy")

        speechEngine?.release()
        speechEngine = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "Error releasing wake lock", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
