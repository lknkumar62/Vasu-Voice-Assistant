package com.vasu.assistant.core.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MicCaptureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CAPTURE_MIC) return

        val seconds = intent.getIntExtra("seconds", 600)
        val dumps = intent.getBooleanExtra("dumps", false)

        Log.d(TAG, "Mic capture requested: ${seconds}s, dumps=$dumps")

        try {
            val serviceIntent = Intent(context, com.vasu.assistant.core.service.VasuForegroundService::class.java).apply {
                action = "CAPTURE_MIC"
                putExtra("seconds", seconds)
                putExtra("dumps", dumps)
            }
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mic capture: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VasuMicCapture"
        const val ACTION_CAPTURE_MIC = "com.vasu.assistant.CAPTURE_MIC"
    }
}
