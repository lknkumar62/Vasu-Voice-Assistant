package com.vasu.ai.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import com.vasu.ai.memory.VasuMemoryStore

/** Reads notification text only after the user explicitly enables Notification Access. */
class VasuNotificationListener : NotificationListenerService() {
    private lateinit var memory: VasuMemoryStore

    override fun onCreate() {
        super.onCreate()
        memory = VasuMemoryStore(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val packageName = sbn.packageName.orEmpty()
        val summary = listOf(title, text).filter { it.isNotBlank() }.joinToString(": ")
        val safeSummary = if (TextUtils.isEmpty(packageName)) summary else "[$packageName] $summary"
        memory.add("[notification]", safeSummary, true, false)
    }
}
