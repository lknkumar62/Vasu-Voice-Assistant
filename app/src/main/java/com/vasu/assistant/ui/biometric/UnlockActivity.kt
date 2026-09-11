package com.vasu.assistant.ui.biometric

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log

class UnlockActivity : Activity() {

    private var callback: UnlockCallback? = null

    interface UnlockCallback {
        fun onUnlockResult(success: Boolean)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // Acquire wake lock if screen is off
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (powerManager != null && !powerManager.isInteractive) {
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "vasu:unlock"
                )
                wakeLock.acquire(10000L)
                wakeLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake lock error: ${e.message}")
        }

        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager == null || !keyguardManager.isKeyguardLocked) {
            Log.d(TAG, "Already unlocked")
            onUnlockResult("already_unlocked")
            return
        }

        Log.d(TAG, "Requesting keyguard dismiss (secure=${keyguardManager.isDeviceSecure})")

        keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                Log.d(TAG, "Keyguard dismissed")
                onUnlockResult("dismissed")
            }

            override fun onDismissCancelled() {
                Log.d(TAG, "Keyguard dismiss cancelled")
                onUnlockResult("cancelled")
            }

            override fun onDismissError() {
                Log.e(TAG, "Keyguard dismiss error")
                onUnlockResult("error")
            }
        })
    }

    private fun onUnlockResult(reason: String) {
        val isLocked = try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked ?: false
        } catch (e: Exception) { false }

        val success = !isLocked
        Log.d(TAG, "Unlock result: success=$success ($reason)")

        callback?.onUnlockResult(success)
        UnlockManager.onUnlockComplete(success)

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        val isLocked = try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.isKeyguardLocked ?: true
        } catch (e: Exception) { true }

        callback?.onUnlockResult(!isLocked)
        callback = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "UnlockActivity"

        fun launch(context: Context): Intent {
            return Intent(context, UnlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
