package com.vasu.assistant.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject lateinit var notificationParser: NotificationParser
    @Inject lateinit var actionManager: NotificationActionManager

    private val listeners = mutableListOf<NotificationCallback>()

    interface NotificationCallback {
        fun onNotificationReceived(notification: ParsedNotification)
    }

    companion object {
        var instance: NotificationListener? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance == this) instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldIgnore(sbn)) return
        val parsed = notificationParser.parse(sbn)
        if (parsed != null) {
            listeners.forEach { it.onNotificationReceived(parsed) }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Notification dismissed
    }

    fun addCallback(callback: NotificationCallback) { listeners.add(callback) }
    fun removeCallback(callback: NotificationCallback) { listeners.remove(callback) }

    fun getActiveParsedNotifications(): List<ParsedNotification> {
        return try {
            val notifications = super.getActiveNotifications() ?: return emptyList()
            notifications.mapNotNull { notificationParser.parse(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun dismissNotification(key: String): Boolean {
        return try {
            cancelNotification(key)
            true
        } catch (e: Exception) { false }
    }

    private fun shouldIgnore(sbn: StatusBarNotification): Boolean {
        val ignoredPackages = listOf(
            "com.android.systemui",
            "com.vasu.assistant"
        )
        return sbn.packageName in ignoredPackages
    }
}
