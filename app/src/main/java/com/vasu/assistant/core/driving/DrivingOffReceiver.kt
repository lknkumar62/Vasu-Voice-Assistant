package com.vasu.assistant.core.driving

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class DrivingOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.vasu.assistant.DRIVING_OFF") return
        Log.d("VasuDrivingOff", "Driving off received")
        // Parity with Maya's DrivingOffReceiver - handles DRIVING_OFF broadcast
    }
}
