package com.vasu.assistant.core.social

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasu.assistant.R
import java.util.Calendar

class SocialAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "SocialAlarmReceiver onReceive: $action")

        when (action) {
            ACTION_DAILY_STORY -> handleDailyStory(context)
            ACTION_QUEUED_POST -> handleQueuedPosts(context)
        }
    }

    private fun handleDailyStory(context: Context) {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_DAILY_ON, false)) {
            Log.d(TAG, "Daily stories disabled")
            return
        }

        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastDay = prefs.getInt(KEY_DAILY_LAST_DAY, -1)
        if (lastDay == today) {
            Log.d(TAG, "Daily story already done today")
            return
        }

        prefs.edit().putInt(KEY_DAILY_LAST_DAY, today).apply()
        Log.d(TAG, "Generating daily story for day $today")

        showStoryNotification(context, today)
    }

    private fun handleQueuedPosts(context: Context) {
        val prefs = getPrefs(context)
        val postsJson = prefs.getString(KEY_QUEUED_POSTS, "[]") ?: "[]"

        // Parse queued posts - simple format: "timestamp|content|platform,..."
        val posts = parseQueuedPosts(postsJson)
        val now = System.currentTimeMillis()
        val validPosts = posts.filter { it.scheduledTime <= now }

        if (validPosts.isEmpty()) {
            Log.d(TAG, "No queued posts due")
            return
        }

        var removedCount = 0
        for (post in validPosts) {
            // Drop stale posts (older than 3 hours)
            if (now - post.scheduledTime > STALE_THRESHOLD_MS) {
                removedCount++
                Log.d(TAG, "Dropping stale post: ${post.content.take(30)}...")
                continue
            }

            showPostNotification(context, post)
            Log.d(TAG, "Processed queued post: ${post.content.take(30)}...")
        }

        // Clean up processed posts
        val remaining = posts.filter { it.scheduledTime > now || (now - it.scheduledTime <= STALE_THRESHOLD_MS) }
        saveQueuedPosts(context, remaining)
    }

    private fun showStoryNotification(context: Context, day: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VASU Social",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily stories and social updates"
        }
        notificationManager.createNotificationChannel(channel)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val quotes = listOf(
            "Aaj ka din bahut khaas hai! Kuch naya seekho aur grow karo!",
            "Har din ek naya mauka hai apne aap ko behtar banane ka!",
            "Mehnat ka fal meetha hota hai — ruko mat, chalte raho!",
            "Khush raho, muskurate raho — duniya aapko wahi degi jo aap doge!",
            "Apne sapno ko chase karo, logon ki baaton se mat ruko!",
            "Chhota start karo, bada socho — success zaroor milegi!",
            "Aaj kuch aisa karo jo kal ka aapka best version banaye!"
        )
        val quote = quotes[day % quotes.size]

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VASU Daily Story")
            .setContentText(quote)
            .setStyle(NotificationCompat.BigTextStyle().bigText(quote))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_DAILY, notification)
    }

    private fun showPostNotification(context: Context, post: QueuedPost) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VASU Social",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VASU Post Ready")
            .setContentText("Post for ${post.platform}: ${post.content.take(50)}...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID_POST + post.hashCode(), notification)
    }

    private fun parseQueuedPosts(json: String): List<QueuedPost> {
        // Simple pipe-delimited format: "timestamp|content|platform,timestamp|content|platform"
        if (json == "[]" || json.isBlank()) return emptyList()
        return try {
            json.split(",").mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size >= 3) {
                    QueuedPost(
                        scheduledTime = parts[0].toLongOrNull() ?: return@mapNotNull null,
                        content = parts[1],
                        platform = parts[2]
                    )
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveQueuedPosts(context: Context, posts: List<QueuedPost>) {
        val json = posts.joinToString(",") { "${it.scheduledTime}|${it.content}|${it.platform}" }
        getPrefs(context).edit().putString(KEY_QUEUED_POSTS, json).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "VasuSocialAlarm"
        private const val PREFS_NAME = "vasu_social"
        private const val CHANNEL_ID = "vasu_social"
        private const val KEY_DAILY_ON = "daily_on"
        private const val KEY_DAILY_LAST_DAY = "daily_last_day"
        private const val KEY_QUEUED_POSTS = "queued_posts"
        private const val NOTIFICATION_ID_DAILY = 20
        private const val NOTIFICATION_ID_POST = 100
        private const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L // 3 hours

        const val ACTION_DAILY_STORY = "com.vasu.assistant.social.DAILY_STORY"
        const val ACTION_QUEUED_POST = "com.vasu.assistant.social.QUEUED_POST"

        fun scheduleDailyStory(context: Context, hour: Int = 9, minute: Int = 0) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, SocialAlarmReceiver::class.java).apply {
                action = ACTION_DAILY_STORY
            }
            val pending = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
            }

            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending
            )
            Log.d(TAG, "Daily story scheduled for $hour:$minute")
        }

        fun enableDailyStories(context: Context, enabled: Boolean) {
            getPrefs(context).edit().putBoolean(KEY_DAILY_ON, enabled).apply()
            if (enabled) scheduleDailyStory(context)
            Log.d(TAG, "Daily stories enabled: $enabled")
        }

        fun queuePost(context: Context, content: String, platform: String, scheduledTime: Long) {
            val prefs = getPrefs(context)
            val existing = prefs.getString(KEY_QUEUED_POSTS, "[]") ?: "[]"
            val newEntry = "$scheduledTime|$content|$platform"
            val updated = if (existing == "[]") newEntry else "$existing,$newEntry"
            prefs.edit().putString(KEY_QUEUED_POSTS, updated).apply()
            Log.d(TAG, "Post queued for $platform at $scheduledTime")
        }

        fun processQueuedPosts(context: Context) {
            val intent = Intent(context, SocialAlarmReceiver::class.java).apply {
                action = ACTION_QUEUED_POST
            }
            context.sendBroadcast(intent)
        }
    }
}

data class QueuedPost(
    val scheduledTime: Long,
    val content: String,
    val platform: String
)
