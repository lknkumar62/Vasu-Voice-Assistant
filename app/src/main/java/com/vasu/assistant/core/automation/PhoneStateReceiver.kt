package com.vasu.assistant.core.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "VasuPhoneState"
        var incomingNumber: String? = null
            private set
        var isRinging = false
            private set
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        if (state == incomingNumber) return
        incomingNumber = state

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                isRinging = true
                Log.d(TAG, "Incoming call from: $number")
                sendToForegroundService(context, "RINGING", number)
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (!isRinging) return
                Log.d(TAG, "Call answered")
                sendToForegroundService(context, "OFFHOOK", number)
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (!isRinging) return
                isRinging = false
                Log.d(TAG, "Call ended")
                sendToForegroundService(context, "IDLE", number)
            }
        }
    }

    private fun sendToForegroundService(context: Context, callState: String, number: String?) {
        try {
            val intent = Intent(context, com.vasu.assistant.core.service.VasuForegroundService::class.java).apply {
                action = "INCOMING_CALL"
                putExtra("call_state", callState)
                putExtra("call_number", number)
            }
            context.startForegroundService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to foreground service: ${e.message}")
        }
    }
}
