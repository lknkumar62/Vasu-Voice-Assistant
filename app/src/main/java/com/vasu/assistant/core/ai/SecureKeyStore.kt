package com.vasu.assistant.core.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed storage for AI provider credentials.
 *
 * The API key is entered by the user at runtime and encrypted with a key held in
 * the Android Keystore, so it is never present in source, BuildConfig, Gradle
 * files, or the APK. It is deliberately never logged.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences? by lazy { openEncrypted() }

    private fun openEncrypted(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context, MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Keystore can fail after a restore-to-new-device or a corrupted keyset.
        // Surface the failure rather than silently degrading to plaintext storage.
        Log.e(TAG, "Encrypted preferences unavailable: ${e.javaClass.simpleName}")
        null
    }

    val isAvailable: Boolean get() = prefs != null

    fun getGeminiKey(): String? =
        prefs?.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setGeminiKey(key: String): Boolean {
        val store = prefs ?: return false
        store.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
        return true
    }

    fun clearGeminiKey(): Boolean {
        val store = prefs ?: return false
        store.edit().remove(KEY_GEMINI_API_KEY).apply()
        return true
    }

    fun hasGeminiKey(): Boolean = getGeminiKey() != null

    /** Safe for display in Settings: reveals only enough to identify the key. */
    fun maskedGeminiKey(): String? {
        val key = getGeminiKey() ?: return null
        return if (key.length <= 8) "*".repeat(key.length)
        else "${key.take(4)}${"*".repeat(key.length - 8)}${key.takeLast(4)}"
    }

    var geminiModel: String
        get() = prefs?.getString(KEY_GEMINI_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) { prefs?.edit()?.putString(KEY_GEMINI_MODEL, value)?.apply() }

    var geminiEnabled: Boolean
        get() = prefs?.getBoolean(KEY_GEMINI_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_GEMINI_ENABLED, value)?.apply() }

    var lastSuccessfulConnection: Long
        get() = prefs?.getLong(KEY_LAST_SUCCESS, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_LAST_SUCCESS, value)?.apply() }

    var lastError: String?
        get() = prefs?.getString(KEY_LAST_ERROR, null)
        set(value) { prefs?.edit()?.putString(KEY_LAST_ERROR, value)?.apply() }

    companion object {
        private const val TAG = "SecureKeyStore"
        private const val FILE_NAME = "vasu_secure_prefs"
        private const val MASTER_KEY_ALIAS = "vasu_master_key"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_GEMINI_ENABLED = "gemini_enabled"
        private const val KEY_LAST_SUCCESS = "gemini_last_success"
        private const val KEY_LAST_ERROR = "gemini_last_error"

        const val DEFAULT_MODEL = "gemini-2.0-flash"

        val AVAILABLE_MODELS = listOf(
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
        )
    }
}
