package com.vasu.assistant.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.view.inputmethod.InputConnection
import com.vasu.assistant.core.ai.AiResult
import com.vasu.assistant.core.ai.GeminiProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationAutoReplyManager(
    private val context: Context,
    private val listener: NotificationListenerService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Real AI provider (the app's Gemini client) resolved from the Hilt graph.
     * Null when Hilt is unavailable — callers fall back to canned replies.
     */
    private val aiProvider: GeminiProvider? by lazy {
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                AutoReplyAiEntryPoint::class.java
            ).geminiProvider()
        } catch (e: Exception) {
            Log.e(TAG, "AI provider unavailable: ${e.message}")
            null
        }
    }

    private val autoReplyQueue = ArrayDeque<PendingAutoReply>()

    data class PendingAutoReply(
        val packageName: String,
        val notificationId: Int,
        val senderName: String,
        val messageText: String,
        val replyActionIndex: Int,
        val timestamp: Long
    )

    fun processNotification(sbn: StatusBarNotification) {
        if (!isEnabled()) return
        if (sbn.packageName in IGNORED_PACKAGES) return
        if (sbn.packageName !in SUPPORTED_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Find reply action
        val replyAction = notification.actions?.firstOrNull { action ->
            action?.getRemoteInputs()?.isNotEmpty() == true
        } ?: return
        val replyIndex = notification.actions.indexOf(replyAction)

        Log.d(TAG, "Auto-reply candidate: $title from ${sbn.packageName}: ${text.take(50)}")

        val reply = PendingAutoReply(
            packageName = sbn.packageName,
            notificationId = sbn.id,
            senderName = title,
            messageText = text,
            replyActionIndex = replyIndex,
            timestamp = System.currentTimeMillis()
        )

        scope.launch {
            delay(2000) // Wait 2s before replying
            processReply(reply)
        }
    }

    private suspend fun processReply(pending: PendingAutoReply) {
        try {
            val replyText = generateReply(pending.senderName, pending.messageText)
            if (replyText.isBlank()) {
                Log.d(TAG, "No reply generated")
                return
            }

            sendReply(pending, replyText)
            Log.d(TAG, "Auto-replied to ${pending.senderName}: $replyText")
        } catch (e: Exception) {
            Log.e(TAG, "Auto-reply failed: ${e.message}")
        }
    }

    private suspend fun generateReply(senderName: String, messageText: String): String {
        // Use AI to generate contextual reply
        return try {
            val prompt = buildString {
                append("Generate a short, friendly, casual reply to this message from $senderName. ")
                append("Message: \"$messageText\". ")
                append("Keep it natural, 1-2 lines max. Don't use quotes in the reply.")
            }
            val provider = aiProvider
            if (provider != null) {
                when (val result = provider.generate(prompt = prompt)) {
                    is AiResult.Text -> result.content
                    else -> getFallbackReply(senderName, messageText)
                }
            } else {
                // Fallback replies
                getFallbackReply(senderName, messageText)
            }
        } catch (e: Exception) {
            getFallbackReply(senderName, messageText)
        }
    }

    private fun getFallbackReply(senderName: String, messageText: String): String {
        val lowerMsg = messageText.lowercase()
        return when {
            lowerMsg.contains("hello") || lowerMsg.contains("hi") -> "Hey! Kya haal hai? 😊"
            lowerMsg.contains("kaise") || lowerMsg.contains("how are you") -> "Main badhiya hoon! Aap batao?"
            lowerMsg.contains("thanks") || lowerMsg.contains("thank you") -> "Most welcome! 🙏"
            lowerMsg.contains("?") -> "Haan ji, batao!"
            else -> "Ok, samajh gaya! 👍"
        }
    }

    private fun sendReply(pending: PendingAutoReply, replyText: String) {
        try {
            val sbn = findActiveNotification(pending.packageName, pending.notificationId) ?: return
            val notification = sbn.notification ?: return
            val action = notification.actions?.getOrNull(pending.replyActionIndex) ?: return

            val remoteInputs = action.getRemoteInputs() ?: return
            if (remoteInputs.isEmpty()) return

            // Build the reply intent
            val intent = action.actionIntent
            if (intent == null) {
                Log.e(TAG, "Reply action intent is null")
                return
            }

            // Fill-in intent that carries the reply results to the app's reply action
            val localIntent = Intent()
            val resultBundle = Bundle()

            for (ri in remoteInputs) {
                resultBundle.putCharSequence(ri.resultKey, replyText)
            }

            // Framework RemoteInput type is used consistently: the array comes from
            // Notification.Action.getRemoteInputs() and is consumed by the framework
            // addResultsToIntent helper on the fill-in intent.
            android.app.RemoteInput.addResultsToIntent(remoteInputs, localIntent, resultBundle)
            intent.send(context, 0, localIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reply: ${e.message}")
        }
    }

    private fun findActiveNotification(packageName: String, id: Int): StatusBarNotification? {
        return try {
            listener.activeNotifications?.firstOrNull {
                it.packageName == packageName && it.id == id
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d(TAG, "Auto-reply enabled: $enabled")
    }

    fun getIgnoredPackages(): Set<String> = prefs.getStringSet(KEY_IGNORED, emptySet()) ?: emptySet()
    fun setIgnoredPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_IGNORED, packages).apply()
    }

    companion object {
        private const val TAG = "VasuAutoReply"
        private const val PREFS_NAME = "vasu_auto_reply"
        private const val KEY_ENABLED = "auto_reply_enabled"
        private const val KEY_IGNORED = "auto_reply_ignored"

        private val SUPPORTED_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "org.thoughtcrime.securesms"
        )

        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.vasu.assistant"
        )
    }
}

/** Hilt entry point exposing the singleton AI client to non-injected classes. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AutoReplyAiEntryPoint {
    fun geminiProvider(): GeminiProvider
}
