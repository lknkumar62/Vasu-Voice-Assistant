package com.vasu.assistant.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Message data
 */
data class Message(
    val address: String,
    val body: String,
    val timestamp: Long,
    val isReceived: Boolean
)

/**
 * MessagingManager - Manages SMS and messaging.
 *
 * Features:
 * - Send SMS
 * - Read messages
 * - Message history
 * - Contact lookup for messaging
 */
@Singleton
class MessagingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactManager: ContactManager
) {
    /**
     * Send an SMS message
     */
    fun sendSms(contactName: String, message: String): ActionResult {
        val contact = findContactForMessaging(contactName)
            ?: return ActionResult.error("send_sms", "Contact not found: $contactName", "Contact not found")

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${contact.phoneNumber}")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("send_sms", "Opening SMS to ${contact.name}")
        } catch (e: Exception) {
            ActionResult.error("send_sms", "Failed to send SMS to ${contact.name}", e.message ?: "Unknown error")
        }
    }

    /**
     * Open WhatsApp chat with a contact
     */
    fun openWhatsApp(contactName: String, message: String = ""): ActionResult {
        val contact = findContactForMessaging(contactName)
            ?: return ActionResult.error("whatsapp", "Contact not found: $contactName", "Contact not found")

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val url = if (message.isNotBlank()) {
                    "https://wa.me/${contact.phoneNumber}?text=${Uri.encode(message)}"
                } else {
                    "https://wa.me/${contact.phoneNumber}"
                }
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("whatsapp", "Opening WhatsApp for ${contact.name}")
        } catch (e: Exception) {
            ActionResult.error("whatsapp", "Failed to open WhatsApp", e.message ?: "Unknown error")
        }
    }

    /**
     * Open email compose
     */
    fun composeEmail(to: String, subject: String = "", body: String = ""): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$to")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("email", "Opening email to $to")
        } catch (e: Exception) {
            ActionResult.error("email", "Failed to open email", e.message ?: "Unknown error")
        }
    }

    /**
     * Read recent SMS messages
     */
    fun getRecentMessages(limit: Int = 10): List<Message> {
        val messages = mutableListOf<Message>()

        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        )?.use { cursor ->
            val addressCol = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyCol = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateCol = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeCol = cursor.getColumnIndex(Telephony.Sms.TYPE)

            while (cursor.moveToNext()) {
                messages.add(
                    Message(
                        address = cursor.getString(addressCol) ?: "",
                        body = cursor.getString(bodyCol) ?: "",
                        timestamp = cursor.getLong(dateCol),
                        isReceived = cursor.getInt(typeCol) == Telephony.Sms.MESSAGE_TYPE_INBOX
                    )
                )
            }
        }

        return messages
    }

    /**
     * Find contact for messaging
     */
    private fun findContactForMessaging(name: String): ContactInfo? {
        return contactManager.findBestMatch(name)
    }
}
