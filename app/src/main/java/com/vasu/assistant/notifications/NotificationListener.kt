package com.vasu.assistant.notifications

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class NotificationListener : NotificationListenerService() {

    @Inject lateinit var notificationParser: NotificationParser
    @Inject lateinit var actionManager: NotificationActionManager

    private val listeners = mutableListOf<NotificationCallback>()
    lateinit var autoReplyManager: NotificationAutoReplyManager
        private set

    interface NotificationCallback {
        fun onNotificationReceived(notification: ParsedNotification)
    }

    companion object {
        private const val TAG = "VasuNotificationListener"

        var instance: NotificationListener? = null
            private set

        var isListening = false
            private set

        var lastCallNotification: ParsedNotification? = null
            private set

        private val callPackages = setOf(
            "com.google.android.dialer",
            "com.android.dialer",
            "com.samsung.android.dialer",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger"
        )

        private val callKeywords = listOf(
            "incoming call", "ongoing call", "call in progress",
            "calling", "missed call", "dialing", "voice call", "video call"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        isListening = true
        autoReplyManager = NotificationAutoReplyManager(this, this)
        Log.d(TAG, "Notification listener connected")
        processActiveNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isListening = false
        if (instance == this) instance = null
        Log.d(TAG, "Notification listener disconnected, requesting rebind")
        try {
            requestRebind(ComponentName(this, NotificationListener::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "Rebind failed: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        if (instance == this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (shouldIgnore(sbn)) return
        val parsed = notificationParser.parse(sbn)
        if (parsed != null) {
            detectCallNotification(sbn, parsed)
            listeners.forEach { it.onNotificationReceived(parsed) }
        }

        // Auto-reply for message notifications
        try {
            autoReplyManager.processNotification(sbn)
        } catch (e: Exception) {
            Log.e(TAG, "Auto-reply error: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg in callPackages) {
            lastCallNotification = null
            Log.d(TAG, "Call notification removed: $pkg")
        }
    }

    private fun detectCallNotification(sbn: StatusBarNotification, parsed: ParsedNotification) {
        val pkg = sbn.packageName
        if (pkg in callPackages) {
            val text = sbn.notification.extras.getString("android.text")?.lowercase() ?: ""
            val title = sbn.notification.extras.getString("android.title")?.lowercase() ?: ""
            val isCall = callKeywords.any { keyword ->
                text.contains(keyword) || title.contains(keyword)
            }
            if (isCall) {
                lastCallNotification = parsed
                Log.d(TAG, "Call notification detected from $pkg")
            }
        }
    }

    private fun processActiveNotifications() {
        try {
            val notifications = activeNotifications ?: return
            for (sbn in notifications) {
                if (shouldIgnore(sbn)) continue
                val parsed = notificationParser.parse(sbn) ?: continue
                detectCallNotification(sbn, parsed)
                listeners.forEach { it.onNotificationReceived(parsed) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing active notifications: ${e.message}")
        }
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
