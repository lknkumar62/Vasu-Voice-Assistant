package com.vasu.assistant.calls

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.CallLog
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vasu.assistant.core.automation.ActionResult
import javax.inject.Inject
import javax.inject.Singleton

data class Contact(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val email: String? = null
)

@Singleton
class CallManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun makeCall(contactName: String): ActionResult {
        val contact = findContact(contactName)
            ?: return ActionResult.error("make_call", "Contact not found: $contactName", "Contact not found")

        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${contact.phoneNumber}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("make_call", "Calling ${contact.name}")
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${contact.phoneNumber}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ActionResult.success("make_call", "Dialing ${contact.name}")
            } catch (e2: Exception) {
                ActionResult.error("make_call", "Failed to call ${contact.name}", e2.message ?: "Unknown error")
            }
        }
    }

    fun callNumber(number: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$number")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("make_call", "Dialing $number")
        } catch (e: Exception) {
            ActionResult.error("make_call", "Failed to dial $number", e.message ?: "Unknown error")
        }
    }

    fun findContact(name: String): Contact? {
        return searchContacts(name).firstOrNull()
    }

    fun searchContacts(query: String): List<Contact> {
        val contacts = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val number = cursor.getString(numberCol) ?: ""
                if (number.isNotBlank()) {
                    contacts.add(Contact(id = id, name = name, phoneNumber = number))
                }
            }
        }

        return contacts.distinctBy { it.phoneNumber }
    }

    fun getCallHistory(limit: Int = 10): List<CallLogEntry> {
        val calls = mutableListOf<CallLogEntry>()

        val projection = arrayOf(
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
        )

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val numberCol = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val typeCol = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateCol = cursor.getColumnIndex(CallLog.Calls.DATE)
            val durationCol = cursor.getColumnIndex(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                calls.add(
                    CallLogEntry(
                        name = cursor.getString(nameCol) ?: "Unknown",
                        number = cursor.getString(numberCol) ?: "",
                        type = when (cursor.getInt(typeCol)) {
                            CallLog.Calls.INCOMING_TYPE -> "Incoming"
                            CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                            CallLog.Calls.MISSED_TYPE -> "Missed"
                            else -> "Unknown"
                        },
                        timestamp = cursor.getLong(dateCol),
                        duration = cursor.getLong(durationCol)
                    )
                )
            }
        }

        return calls
    }
}

data class CallLogEntry(
    val name: String,
    val number: String,
    val type: String,
    val timestamp: Long,
    val duration: Long
)
