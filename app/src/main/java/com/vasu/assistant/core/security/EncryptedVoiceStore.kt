package com.vasu.assistant.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted-at-rest persistence for Guardian enrollments.
 *
 * Voice embeddings are biometric data, so they are stored in
 * EncryptedSharedPreferences (Android Keystore master key), mirroring
 * [com.vasu.assistant.core.ai.SecureKeyStore].
 *
 * Design notes:
 * - A mode marker makes the encrypted/plain choice sticky across launches, so
 *   a one-time Keystore failure cannot orphan previously written data in the
 *   other file (split-brain loss).
 * - A corrupt payload logs an error and loads as empty (guardian off).
 *   Trade-off: failing closed here (guardian on, zero voices) would lock the
 *   user out of the device entirely, since UNKNOWN fails every permission
 *   check; re-enrolling is recoverable, a lockout is not.
 * - One malformed voice entry skips only that entry, not the whole snapshot.
 */
@Singleton
class EncryptedVoiceStore @Inject constructor(
    @ApplicationContext private val context: Context
) : VoiceStore {

    private val modePrefs: SharedPreferences =
        context.getSharedPreferences(FILE_MODE, Context.MODE_PRIVATE)

    private val prefs: SharedPreferences by lazy { openPrefs() }

    private fun openPrefs(): SharedPreferences {
        if (modePrefs.getString(KEY_MODE, null) == MODE_PLAIN) {
            return context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
        }

        val encrypted = try {
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
            Log.e(TAG, "Encrypted preferences unavailable: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        if (encrypted != null) {
            modePrefs.edit().putString(KEY_MODE, MODE_ENC).apply()
            return encrypted
        }

        Log.w(TAG, "Falling back to SharedPreferences for guardian data (stored unencrypted)")
        modePrefs.edit().putString(KEY_MODE, MODE_PLAIN).apply()
        return context.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
    }

    override fun load(): VoiceStoreSnapshot {
        return try {
            val raw = prefs.getString(KEY_STATE, null) ?: return VoiceStoreSnapshot()
            decode(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Corrupt guardian payload — starting with no enrollments: ${e.message}")
            VoiceStoreSnapshot()
        }
    }

    override fun save(snapshot: VoiceStoreSnapshot) {
        try {
            // commit(): this payload is small and callers are user-action rate;
            // durability beats async write-loss here.
            val ok = prefs.edit().putString(KEY_STATE, encode(snapshot)).commit()
            if (!ok) Log.e(TAG, "Guardian state write rejected by storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist guardian state: ${e.message}")
        }
    }

    private fun encode(snapshot: VoiceStoreSnapshot): String {
        val root = JSONObject()
        root.put("guardianEnabled", snapshot.guardianEnabled)
        val array = JSONArray()
        for (voice in snapshot.voices) {
            val embedding = JSONArray()
            for (value in voice.embedding) {
                // JSONObject.put rejects NaN/Infinity — never let one bad
                // float silently void the whole save.
                embedding.put(if (value.isFinite()) value.toDouble() else 0.0)
            }
            array.put(
                JSONObject()
                    .put("id", voice.id)
                    .put("name", voice.name)
                    .put("roleName", voice.roleName)
                    .put("embedding", embedding)
                    .put("enrolledAt", voice.enrolledAt)
                    .put("lastVerified", voice.lastVerified)
                    .put("verificationCount", voice.verificationCount)
            )
        }
        root.put("voices", array)
        return root.toString()
    }

    private fun decode(raw: String): VoiceStoreSnapshot {
        val root = JSONObject(raw)
        val enabled = root.optBoolean("guardianEnabled", false)
        val array = root.optJSONArray("voices")
            ?: return VoiceStoreSnapshot(guardianEnabled = enabled)

        val voices = ArrayList<StoredVoice>(array.length())
        for (i in 0 until array.length()) {
            try {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id", "")
                if (id.isEmpty()) continue

                val embeddingArray = obj.optJSONArray("embedding") ?: continue
                val embedding = FloatArray(embeddingArray.length()) { j ->
                    val d = embeddingArray.optDouble(j, 0.0)
                    if (d.isFinite()) d.toFloat() else 0f
                }

                voices.add(
                    StoredVoice(
                        id = id,
                        name = obj.optString("name", ""),
                        roleName = obj.optString("roleName", ""),
                        embedding = embedding,
                        enrolledAt = obj.optLong("enrolledAt", 0L),
                        lastVerified = obj.optLong("lastVerified", 0L),
                        verificationCount = obj.optInt("verificationCount", 0)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed voice entry $i: ${e.message}")
            }
        }
        return VoiceStoreSnapshot(voices = voices, guardianEnabled = enabled)
    }

    companion object {
        private const val TAG = "EncryptedVoiceStore"
        private const val FILE_MODE = "vasu_guardian_mode"
        private const val FILE_NAME = "vasu_guardian"
        private const val FILE_NAME_PLAIN = "vasu_guardian_plain"
        private const val MASTER_KEY_ALIAS = "vasu_guardian_master_key"
        private const val KEY_MODE = "storage_mode"
        private const val KEY_STATE = "guardian_state"
        private const val MODE_ENC = "encrypted"
        private const val MODE_PLAIN = "plain"
    }
}
