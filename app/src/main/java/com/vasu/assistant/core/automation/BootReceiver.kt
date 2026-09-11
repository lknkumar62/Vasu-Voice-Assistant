package com.vasu.assistant.core.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vasu.assistant.core.service.VasuForegroundService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d(TAG, "Boot completed, starting foreground service")
            try {
                val serviceIntent = Intent(context, VasuForegroundService::class.java).apply {
                    action = "BOOT_STANDBY"
                }
                context.startForegroundService(serviceIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service on boot: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "VasuBootReceiver"
    }
}
