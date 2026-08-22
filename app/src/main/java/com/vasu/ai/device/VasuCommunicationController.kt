package com.vasu.ai.device

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class VasuCommunicationController(private val context: Context) {

    fun callContact(name: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
        val number = resolvePhoneNumber(name) ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        }.getOrDefault(false)
    }

    fun sendSms(name: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return false
        if (message.isBlank()) return false
        val number = resolvePhoneNumber(name) ?: return false
        return runCatching {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            true
        }.getOrDefault(false)
    }

    private fun resolvePhoneNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")
        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }
}
