package com.vasu.assistant.core.automation

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasu.assistant.R

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderText = intent.getStringExtra(EXTRA_REMINDER_TEXT) ?: return
        if (reminderText.isBlank()) return

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, System.currentTimeMillis())
        val repeatMinutes = intent.getLongExtra(EXTRA_REPEAT_MINUTES, 0L)

        // Schedule next repeat if needed
        if (repeatMinutes > 0) {
            scheduleNext(context, reminderText, reminderId, repeatMinutes)
        }

        // Show notification
        showNotification(context, reminderText, reminderId)
        Log.d(TAG, "Reminder fired: $reminderText")
    }

    private fun scheduleNext(
        context: Context,
        text: String,
        id: Long,
        repeatMinutes: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextTime = System.currentTimeMillis() + (repeatMinutes * 60_000)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TEXT, text)
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_REPEAT_MINUTES, repeatMinutes)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (id % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pending)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pending)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTime, pending)
        }
    }

    private fun showNotification(context: Context, text: String, id: Long) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VASU Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Scheduled reminders from VASU"
        }
        notificationManager.createNotificationChannel(channel)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VASU Reminder")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((id % Int.MAX_VALUE).toInt(), notification)
    }

    companion object {
        private const val TAG = "VasuReminderReceiver"
        private const val CHANNEL_ID = "vasu_reminders"
        const val EXTRA_REMINDER_TEXT = "reminder_text"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_REPEAT_MINUTES = "reminder_repeat_minutes"

        fun schedule(
            context: Context,
            text: String,
            triggerAtMillis: Long,
            repeatMinutes: Long = 0L
        ): Long {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val id = System.currentTimeMillis()

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(EXTRA_REMINDER_TEXT, text)
                putExtra(EXTRA_REMINDER_ID, id)
                putExtra(EXTRA_REPEAT_MINUTES, repeatMinutes)
            }
            val pending = PendingIntent.getBroadcast(
                context,
                (id % Int.MAX_VALUE).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
                }
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }

            Log.d(TAG, "Reminder scheduled for $triggerAtMillis: $text")
            return id
        }

        fun cancel(context: Context, id: Long) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context,
                (id % Int.MAX_VALUE).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pending)
            Log.d(TAG, "Reminder cancelled: $id")
        }
    }
}
