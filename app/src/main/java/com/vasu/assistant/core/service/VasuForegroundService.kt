package com.vasu.assistant.core.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class VasuForegroundService : Service() {

    @Inject lateinit var wakeWordListener: com.vasu.assistant.core.wakeword.WakeWordDetector

    private val channelId = "vasu_service"
    private val notificationId = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(notificationId, buildNotification("VASU is listening..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                // Wake word detector would be started here
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_UPDATE -> {
                val text = intent.getStringExtra(EXTRA_STATUS) ?: "VASU is active"
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(notificationId, buildNotification(text))
            }
            else -> {
                startForeground(notificationId, buildNotification("VASU is active"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            }
        }
        return START_STICKY
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
