package com.vasu.assistant.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationActionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun replyToNotification(notification: ParsedNotification, replyText: String): ActionResult {
        val replyAction = notification.actions.find { it.title.contains("Reply", ignoreCase = true) }
        if (replyAction?.actionIntent != null) {
            return try {
                val intent = Intent().apply {
                    putExtra("android.intent.extra.REPLY", replyText)
                }
                replyAction.actionIntent.send(context, 0, intent)
                ActionResult.success("reply", "Reply sent to ${notification.appName}")
            } catch (e: Exception) {
                ActionResult.error("reply", "Failed to reply", e.message ?: "Unknown")
            }
        }
        return ActionResult.error("reply", "No reply action available", "Notification from ${notification.appName} doesn't support quick reply")
    }

    fun dismissNotification(packageName: String, notificationId: Int): ActionResult {
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nm.cancelNotification(packageName, notificationId, 0)
            } else {
                nm.cancel(notificationId)
            }
            ActionResult.success("dismiss", "Notification dismissed")
        } catch (e: Exception) {
            ActionResult.error("dismiss", "Failed to dismiss", e.message ?: "Unknown")
        }
    }

    fun performNotificationAction(notification: ParsedNotification, actionIndex: Int): ActionResult {
        val action = notification.actions.getOrNull(actionIndex)
            ?: return ActionResult.error("notif_action", "Action not found at index $actionIndex")
        return try {
            action.actionIntent?.send()
            ActionResult.success("notif_action", "Performed: ${action.title}")
        } catch (e: Exception) {
            ActionResult.error("notif_action", "Failed to perform action", e.message ?: "Unknown")
        }
    }

    fun summarizeNotifications(notifications: List<ParsedNotification>): Map<String, Any> {
        val grouped = notifications.groupBy { it.packageName }
        val summaries = grouped.map { (pkg, notifs) ->
            mapOf(
                "app" to notifs.first().appName,
                "count" to notifs.size,
                "latest" to (notifs.maxByOrNull { it.timestamp }?.title ?: ""),
                "latestText" to (notifs.maxByOrNull { it.timestamp }?.text ?: "")
            )
        }
        return mapOf(
            "total" to notifications.size,
            "apps" to summaries
        )
    }
}
