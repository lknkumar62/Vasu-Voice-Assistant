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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.core.wakeword.WakeWordDetector
import com.vasu.assistant.core.wakeword.WakeWordState
import com.vasu.assistant.ui.overlay.AssistantOverlayActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VasuForegroundService : Service() {

    @Inject lateinit var wakeWordListener: WakeWordDetector

    private val channelId = "vasu_service"
    private val notificationId = 1001
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                wakeWordListener.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_STATUS) ?: "VASU is active"
                notify(text)
            }
            else -> beginListening()
        }
        return START_STICKY
    }

    private fun beginListening() {
        if (!hasMicPermission()) {
            goForeground("Microphone permission needed - open VASU to grant it", withMic = false)
            return
        }

        goForeground("Starting wake word...", withMic = true)
        wakeWordListener.initialize()

        when (wakeWordListener.state.value) {
            WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR ->
                notify(wakeWordListener.unavailableReason.value ?: "Wake word unavailable")
            else -> {
                wakeWordListener.start()
                observeDetector()
            }
        }
    }

    private fun observeDetector() {
        scope.launch {
            wakeWordListener.state.collect { state ->
                notify(
                    when (state) {
                        WakeWordState.LISTENING -> "Listening for \"Hello Vasu\""
                        WakeWordState.DETECTED -> {
                            AssistantOverlayActivity.launch(this@VasuForegroundService)
                            "Heard you - opening VASU"
                        }
                        WakeWordState.MODEL_NOT_AVAILABLE, WakeWordState.ERROR ->
                            wakeWordListener.unavailableReason.value ?: "Wake word unavailable"
                        WakeWordState.IDLE -> "VASU is active"
                    }
                )
            }
        }

        scope.launch {
            wakeWordListener.detections.collect {
                AssistantOverlayActivity.launch(this@VasuForegroundService)
            }
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

    private fun notify(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, buildNotification(status))
    }

    override fun onDestroy() {
        scope.cancel()
        wakeWordListener.stop()
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

    companion object {
        const val ACTION_START = "com.vasu.assistant.START"
        const val ACTION_STOP = "com.vasu.assistant.STOP"
        const val ACTION_UPDATE = "com.vasu.assistant.UPDATE"
        const val EXTRA_STATUS = "status"

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

        fun updateStatus(context: Context, status: String) {
            context.startService(Intent(context, VasuForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_STATUS, status)
            })
        }
    }
}
