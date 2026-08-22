package com.vasu.ai.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Reads notification text only after the user explicitly enables Notification Access. */
class VasuNotificationListener : NotificationListenerService() {

    companion object {
        private const val MAX_ITEMS = 30
        private val lock = Any()
        private val recentItems = ArrayDeque<NotificationItem>()

        fun recent(limit: Int = 20): List<NotificationItem> = synchronized(lock) {
            recentItems.takeLast(limit.coerceIn(1, MAX_ITEMS)).asReversed()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val item = NotificationItem(
            time = System.currentTimeMillis(),
            packageName = sbn.packageName.orEmpty(),
            title = title.take(300),
            text = text.take(1000)
        )
        synchronized(lock) {
            recentItems.addLast(item)
            while (recentItems.size > MAX_ITEMS) recentItems.removeFirst()
        }
    }

    data class NotificationItem(
        val time: Long,
        val packageName: String,
        val title: String,
        val text: String
    )
}
