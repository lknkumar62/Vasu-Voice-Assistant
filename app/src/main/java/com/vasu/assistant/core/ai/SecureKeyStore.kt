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

    fun getGeminiKey(): String? {
        val saved = prefs?.getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }
    }

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

    /**
     * Whether the provider may substitute a configured fallback model when the
     * chosen one is unavailable. Off means a wrong choice fails loudly, which some
     * users want: a silent switch changes cost and quality without saying so.
     */
    var allowModelFallback: Boolean
        get() = prefs?.getBoolean(KEY_ALLOW_FALLBACK, AiProviderConfig.GEMINI.allowFallback)
            ?: AiProviderConfig.GEMINI.allowFallback
        set(value) { prefs?.edit()?.putBoolean(KEY_ALLOW_FALLBACK, value)?.apply() }

    /**
     * Chat models this key was last seen to support, read from the provider's own
     * catalogue. Cached so the picker offers what the key can actually use, and
     * cleared whenever reality disagrees with it.
     */
    var discoveredModels: Set<String>
        get() = prefs?.getStringSet(KEY_DISCOVERED_MODELS, emptySet())?.toSet() ?: emptySet()
        set(value) { prefs?.edit()?.putStringSet(KEY_DISCOVERED_MODELS, value)?.apply() }

    /** The model that last answered, which may be a fallback rather than the choice. */
    var activeModel: String?
        get() = prefs?.getString(KEY_ACTIVE_MODEL, null)
        set(value) { prefs?.edit()?.putString(KEY_ACTIVE_MODEL, value)?.apply() }

    var geminiEnabled: Boolean
        get() = prefs?.getBoolean(KEY_GEMINI_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_GEMINI_ENABLED, value)?.apply() }

    fun getClaudeKey(): String? {
        val saved = prefs?.getString(KEY_CLAUDE_API_KEY, null)?.takeIf { it.isNotBlank() }
        if (saved != null) return saved
        return System.getenv("CLAUDE_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("OMNIROUTE_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("CLAUDE_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("ANTHROPIC_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getProperty("OMNIROUTE_API_KEY")?.takeIf { it.isNotBlank() }
    }

    fun setClaudeKey(key: String): Boolean {
        val store = prefs ?: return false
        store.edit().putString(KEY_CLAUDE_API_KEY, key.trim()).apply()
        return true
    }

    fun clearClaudeKey(): Boolean {
        val store = prefs ?: return false
        store.edit().remove(KEY_CLAUDE_API_KEY).apply()
        return true
    }

    fun hasClaudeKey(): Boolean = getClaudeKey() != null

    fun maskedClaudeKey(): String? {
        val key = getClaudeKey() ?: return null
        return if (key.length <= 8) "*".repeat(key.length)
        else "${key.take(4)}${"*".repeat(key.length - 8)}${key.takeLast(4)}"
    }

    var claudeModel: String
        get() = prefs?.getString(KEY_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL) ?: DEFAULT_CLAUDE_MODEL
        set(value) { prefs?.edit()?.putString(KEY_CLAUDE_MODEL, value)?.apply() }

    var claudeBaseUrl: String
        get() = prefs?.getString(KEY_CLAUDE_BASE_URL, DEFAULT_CLAUDE_BASE_URL) ?: DEFAULT_CLAUDE_BASE_URL
        set(value) { prefs?.edit()?.putString(KEY_CLAUDE_BASE_URL, value.trim().trimEnd('/'))?.apply() }

    var claudeEnabled: Boolean
        get() = prefs?.getBoolean(KEY_CLAUDE_ENABLED, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_CLAUDE_ENABLED, value)?.apply() }

    var selectedProvider: String
        get() = prefs?.getString(KEY_SELECTED_PROVIDER, "gemini") ?: "gemini"
        set(value) { prefs?.edit()?.putString(KEY_SELECTED_PROVIDER, value)?.apply() }

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
        private const val KEY_CLAUDE_API_KEY = "claude_api_key"
        private const val KEY_CLAUDE_MODEL = "claude_model"
        private const val KEY_CLAUDE_BASE_URL = "claude_base_url"
        private const val KEY_CLAUDE_ENABLED = "claude_enabled"
        private const val KEY_SELECTED_PROVIDER = "selected_provider"
        private const val KEY_LAST_SUCCESS = "gemini_last_success"
        private const val KEY_LAST_ERROR = "gemini_last_error"
        private const val KEY_ALLOW_FALLBACK = "gemini_allow_fallback"
        private const val KEY_DISCOVERED_MODELS = "gemini_discovered_models"
        private const val KEY_ACTIVE_MODEL = "gemini_active_model"

        val DEFAULT_MODEL: String = AiProviderConfig.GEMINI.primaryModel
        val DEFAULT_CLAUDE_MODEL: String = AiProviderConfig.CLAUDE.primaryModel
        val DEFAULT_CLAUDE_BASE_URL: String = AiProviderConfig.CLAUDE.baseUrl
    }
}
