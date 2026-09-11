package com.vasu.assistant.ui.biometric

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnlockManager @Inject constructor() {

    private var pendingUnlock: CompletableDeferred<Boolean>? = null

    data class UnlockState(
        val isDeviceSecure: Boolean = false,
        val isLockScreenEnabled: Boolean = false,
        val lastUnlockTime: Long = 0
    )

    suspend fun requestUnlock(context: Context): Boolean = withContext(Dispatchers.IO) {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (keyguardManager == null || !keyguardManager.isKeyguardLocked) {
            Log.d(TAG, "Device already unlocked")
            return@withContext true
        }

        if (!isLockScreenEnabled(context)) {
            Log.d(TAG, "Lock screen disabled in VASU")
            return@withContext true
        }

        Log.d(TAG, "Requesting unlock...")
        val deferred = CompletableDeferred<Boolean>()
        pendingUnlock = deferred

        val intent = UnlockActivity.launch(context)
        context.startActivity(intent)

        deferred.await()
    }

    fun onUnlockComplete(success: Boolean) {
        Log.d(TAG, "Unlock complete: success=$success")
        pendingUnlock?.complete(success)
        pendingUnlock = null
    }

    fun isDeviceSecure(context: Context): Boolean {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.isDeviceSecure ?: false
        } catch (e: Exception) { false }
    }

    fun isLockScreenEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOCK_ENABLED, false)
    }

    fun setLockScreenEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOCK_ENABLED, enabled).apply()
        Log.d(TAG, "Lock screen enabled: $enabled")
    }

    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                keyguardManager?.isKeyguardSecure ?: false
            } else {
                false
            }
        } catch (e: Exception) { false }
    }

    fun getLastUnlockTime(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_UNLOCK, 0)
    }

    fun setLastUnlockTime(context: Context, time: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_UNLOCK, time).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "UnlockManager"
        private const val PREFS_NAME = "vasu_biometric"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LAST_UNLOCK = "last_unlock"

        private var instance: UnlockManager? = null

        fun getInstance(): UnlockManager {
            return instance ?: UnlockManager().also { instance = it }
        }

        fun onUnlockComplete(success: Boolean) {
            instance?.onUnlockComplete(success)
        }
    }
}
