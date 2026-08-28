package com.vasu.assistant.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WhatsAppAutomation - WhatsApp-specific messaging automation.
 *
 * Uses accessibility service for advanced WhatsApp interactions
 * and deep links for basic operations.
 */
@Singleton
class WhatsAppAutomation @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contactManager: ContactManager
) {
    private val packageName = "com.whatsapp"

    /**
     * Send a WhatsApp message to a contact
     */
    fun sendMessage(contactName: String, message: String): ActionResult {
        val contact = contactManager.findBestMatch(contactName)
            ?: return ActionResult.error("whatsapp_send", "Contact not found: $contactName", "Contact not found")

        return try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val url = if (message.isNotBlank()) {
                    "https://wa.me/${contact.phoneNumber}?text=${Uri.encode(message)}"
                } else {
                    "https://wa.me/${contact.phoneNumber}"
                }
                data = Uri.parse(url)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("whatsapp_send", "Opening WhatsApp to send message to ${contact.name}")
        } catch (e: Exception) {
            // Fallback: open WhatsApp
            openWhatsApp()
        }
    }

    /**
     * Open WhatsApp
     */
    fun openWhatsApp(): ActionResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult.success("whatsapp_open", "Opened WhatsApp")
            } else {
                ActionResult.error("whatsapp_open", "WhatsApp not installed", "Package not found")
            }
        } catch (e: Exception) {
            ActionResult.error("whatsapp_open", "Failed to open WhatsApp", e.message ?: "Unknown error")
        }
    }

    /**
     * Check if WhatsApp is installed
     */
    fun isInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
