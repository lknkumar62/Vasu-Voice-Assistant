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
     * Send an SMS message (opens the composer prefilled — the OS-level send
     * button is the final confirmation). Accepts a contact name or raw number.
     */
    fun sendSms(contactName: String, message: String): ActionResult {
        val (name, number) = when (val r = resolveAddress(contactName)) {
            is AddressResolution.Found -> r
            is AddressResolution.Ambiguous -> return ambiguousContact("send_sms", contactName, r.names)
            AddressResolution.NotFound ->
                return ActionResult.error("send_sms", "Contact not found: $contactName", "Contact not found")
        }

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("send_sms", "Opening SMS to $name — press send to deliver")
        } catch (e: Exception) {
            ActionResult.error("send_sms", "Failed to open SMS for $name", e.message ?: "Unknown error")
        }
    }

    /**
     * Place a phone call (opens the dialer pre-filled via ACTION_DIAL, so the
     * user always gives the final OS-level confirmation before the call starts).
     * Accepts either a raw phone number or a contact name.
     */
    fun makeCall(contactOrNumber: String): ActionResult {
        val (name, number) = when (val r = resolveAddress(contactOrNumber)) {
            is AddressResolution.Found -> r
            is AddressResolution.Ambiguous -> return ambiguousContact("make_call", contactOrNumber, r.names)
            AddressResolution.NotFound ->
                return ActionResult.error("make_call", "Contact not found: $contactOrNumber", "Contact not found")
        }

        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("make_call", "Dialing $name ($number)")
        } catch (e: Exception) {
            ActionResult.error("make_call", "Failed to place call", e.message ?: "Unknown error")
        }
    }

    /**
     * Open WhatsApp chat with a contact (accepts a contact name or raw number)
     */
    fun openWhatsApp(contactName: String, message: String = ""): ActionResult {
        val (name, number) = when (val r = resolveAddress(contactName)) {
            is AddressResolution.Found -> r
            is AddressResolution.Ambiguous -> return ambiguousContact("whatsapp", contactName, r.names)
            AddressResolution.NotFound ->
                return ActionResult.error("whatsapp", "Contact not found: $contactName", "Contact not found")
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val url = if (message.isNotBlank()) {
                    "https://wa.me/$number?text=${Uri.encode(message)}"
                } else {
                    "https://wa.me/$number"
                }
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("whatsapp", "Opening WhatsApp for $name")
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
     * FIX-8: how a spoken name/number resolved against the contacts provider.
     */
    private sealed interface AddressResolution {
        data class Found(val display: String, val number: String) : AddressResolution
        data class Ambiguous(val names: List<String>) : AddressResolution
        data object NotFound : AddressResolution
    }

    /**
     * Resolves a contact name OR a raw phone number. Strings containing
     * letters go through the contacts provider; anything else is treated as a
     * number the user typed directly. Multiple equally-ranked contacts are
     * reported as [AddressResolution.Ambiguous] — never silently picked.
     */
    private fun resolveAddress(contactOrNumber: String): AddressResolution {
        val trimmed = contactOrNumber.trim()
        if (trimmed.isEmpty()) return AddressResolution.NotFound
        if (!trimmed.any { it.isLetter() }) return AddressResolution.Found(trimmed, trimmed)
        val matches = contactManager.findMatches(trimmed)
        return when {
            matches.isEmpty() -> AddressResolution.NotFound
            matches.size == 1 -> AddressResolution.Found(matches[0].name, matches[0].phoneNumber)
            else -> AddressResolution.Ambiguous(matches.take(4).map { it.name })
        }
    }

    /** Speakable error when a name matched several contacts. */
    private fun ambiguousContact(action: String, query: String, names: List<String>): ActionResult =
        ActionResult.error(
            action,
            "Multiple contacts match \"$query\": ${names.joinToString(", ")} — please say the full name",
            "Say the full name or a phone number"
        )
}
