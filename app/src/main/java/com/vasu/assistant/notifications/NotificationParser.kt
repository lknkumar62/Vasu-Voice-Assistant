package com.vasu.assistant.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedNotification(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val bigText: String?,
    val timestamp: Long,
    val formattedTime: String,
    val category: String,
    val priority: Int,
    val isGroupSummary: Boolean,
    val actions: List<NotificationActionItem>,
    val extras: Map<String, String>
)

data class NotificationActionItem(
    val index: Int,
    val title: String,
    val actionIntent: android.app.PendingIntent?
)

@Singleton
class NotificationParser @Inject constructor() {

    private val sensitivePackages = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
        "com.google.android.gm", "com.microsoft.office.outlook"
    )

    fun parse(sbn: StatusBarNotification): ParsedNotification? {
        return try {
            val notification = sbn.notification ?: return null
            val extras = notification.extras ?: return null

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            val category = notification.category ?: "unknown"
            val priority = notification.priority

            val actionItems = notification.actions?.mapIndexed { index, action ->
                NotificationActionItem(
                    index = index,
                    title = action.title?.toString() ?: "Action $index",
                    actionIntent = action.actionIntent
                )
            } ?: emptyList()

            val extraMap = mutableMapOf<String, String>()
            extras.keySet()?.forEach { key ->
                extras.getCharSequence(key)?.let { extraMap[key] = it.toString() }
            }

            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())

            ParsedNotification(
                id = sbn.id,
                packageName = sbn.packageName,
                appName = getAppName(sbn.packageName),
                title = title,
                text = text,
                bigText = bigText,
                timestamp = sbn.postTime,
                formattedTime = sdf.format(sbn.postTime),
                category = category,
                priority = priority,
                isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                actions = actionItems,
                extras = extraMap
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isSensitive(packageName: String): Boolean = packageName in sensitivePackages

    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
            "org.telegram.messenger" -> "Telegram"
            "com.google.android.gm" -> "Gmail"
            "com.google.android.apps.messaging" -> "Messages"
            "com.instagram.android" -> "Instagram"
            "com.twitter.android" -> "Twitter/X"
            "com.google.android.apps.maps" -> "Maps"
            "com.spotify.music" -> "Spotify"
            else -> packageName.substringAfterLast(".")
        }
    }
}
