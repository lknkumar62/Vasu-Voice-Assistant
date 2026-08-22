package com.vasu.ai.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Android Keystore-backed storage for the optional Picovoice wake-word AccessKey. */
class VasuWakeWordKeyStore(context: Context) {
    private val appContext = context.applicationContext

    private companion object {
        const val KEY_ALIAS = "vasu_wakeword_key"
        const val PREFS = "vasu_wakeword"
        const val VALUE = "encrypted_access_key"
        const val IV = "iv"
    }

    fun hasAccessKey(): Boolean = !getAccessKey().isNullOrBlank()

    fun saveAccessKey(accessKey: String) {
        require(accessKey.isNotBlank())
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(accessKey.toByteArray(StandardCharsets.UTF_8))

        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(VALUE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun getAccessKey(): String? {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val value = prefs.getString(VALUE, null) ?: return null
        val iv = prefs.getString(IV, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(value, Base64.NO_WRAP)),
                StandardCharsets.UTF_8
            )
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun clearAccessKey() {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
