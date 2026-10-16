package com.vasu.assistant.core.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasu.assistant.MainActivity
import com.vasu.assistant.core.service.VasuForegroundService
import com.vasu.assistant.core.settings.VasuSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class BootReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface BootEntryPoint {
        fun settings(): VasuSettings
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return

        if (!isWakeWordEnabled(context)) {
            Log.d(TAG, "Wake word disabled - not starting the service on boot")
            return
        }

        if (Build.VERSION.SDK_INT < 30) {
            Log.d(TAG, "Boot completed - starting foreground service")
            try {
                val serviceIntent = Intent(context, VasuForegroundService::class.java).apply {
                    setAction(VasuForegroundService.ACTION_START)
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service on boot: ${e.message}")
            }
        } else {
            // Android 11+ denies microphone access to foreground services started
            // from BOOT_COMPLETED (while-in-use rule) and Android 14+ refuses the
            // start outright (ForegroundServiceStartNotAllowedException). Ask the
            // user to open VASU instead.
            Log.d(TAG, "Android 11+: mic FGS not usable from boot - posting resume notification")
            postResumeNotification(context)
        }
    }

    private fun isWakeWordEnabled(context: Context): Boolean = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            BootEntryPoint::class.java
        ).settings().wakeWordEnabled.value
    } catch (e: Exception) {
        Log.e(TAG, "Could not read wake word setting: ${e.message}")
        false
    }

    private fun postResumeNotification(context: Context) {
        try {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "VASU Startup",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Asks you to resume VASU wake-word listening after a reboot"
                    setShowBadge(false)
                }
            )
            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(VasuForegroundService.EXTRA_RESUME_WAKE_WORD, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("VASU is ready")
                .setContentText("Tap to resume \"Hello Vasu\" listening after reboot")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openApp)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post resume notification: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VasuBootReceiver"
        private const val CHANNEL_ID = "vasu_startup"
        private const val NOTIFICATION_ID = 1002

        /** Distinct from the service notification's request code 0: PendingIntent
         *  identity ignores extras, so the codes must differ to keep the
         *  resume extra from being clobbered by the other notification. */
        private const val REQUEST_CODE = 2
    }
}
