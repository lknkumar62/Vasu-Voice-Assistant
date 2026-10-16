package com.vasu.assistant.messaging

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ContactManager - Manages contact lookups and operations.
 *
 * Provides contact search, lookup, and information retrieval.
 */
@Singleton
class ContactManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Search contacts by name
     */
    fun searchContacts(query: String): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val number = cursor.getString(numberCol) ?: ""
                val photo = cursor.getString(photoCol)

                if (number.isNotBlank()) {
                    contacts.add(
                        ContactInfo(
                            id = id,
                            name = name,
                            phoneNumber = number,
                            photoUri = photo
                        )
                    )
                }
            }
        }

        return contacts.distinctBy { it.phoneNumber }
    }

    /**
     * Get contact by ID
     */
    fun getContactById(id: Long): ContactInfo? {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                return ContactInfo(
                    id = id,
                    name = cursor.getString(nameCol) ?: "",
                    phoneNumber = cursor.getString(numberCol) ?: ""
                )
            }
        }

        return null
    }

    /**
     * Get all contacts
     */
    fun getAllContacts(): List<ContactInfo> {
        val contacts = mutableListOf<ContactInfo>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val number = cursor.getString(numberCol) ?: ""

                if (number.isNotBlank()) {
                    contacts.add(
                        ContactInfo(id = id, name = name, phoneNumber = number)
                    )
                }
            }
        }

        return contacts.distinctBy { it.phoneNumber }
    }

    /**
     * FIX-8: ranked matches — exact name first, then prefix, then any contains
     * hit. Returns ALL top-ranked matches so callers can disambiguate instead
     * of silently picking an arbitrary entry.
     */
    fun findMatches(name: String): List<ContactInfo> {
        val query = name.trim()
        if (query.isEmpty()) return emptyList()
        val contacts = searchContacts(query)
        if (contacts.isEmpty()) return emptyList()

        val exact = contacts.filter { it.name.equals(query, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact

        val startsWith = contacts.filter { it.name.startsWith(query, ignoreCase = true) }
        if (startsWith.isNotEmpty()) return startsWith

        return contacts
    }

    /**
     * Find best matching contact for a name — first of [findMatches].
     * Callers that must not guess should use [findMatches] and handle
     * multiple results.
     */
    fun findBestMatch(name: String): ContactInfo? = findMatches(name).firstOrNull()
}

/**
 * Contact information
 */
data class ContactInfo(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: String? = null
)
