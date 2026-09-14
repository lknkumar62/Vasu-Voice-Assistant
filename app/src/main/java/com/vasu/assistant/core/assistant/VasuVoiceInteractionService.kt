package com.vasu.assistant.core.assistant

import android.content.Intent
import android.service.voice.VoiceInteractionService
import android.util.Log

class VasuVoiceInteractionService : VoiceInteractionService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: VASU VoiceInteractionService started")
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "onReady: VASU VoiceInteractionService ready")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "onShutdown")
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.d(TAG, "onLaunchVoiceAssistFromKeyguard")
        launchOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun launchOverlay() {
        try {
            val intent = Intent(this, com.vasu.assistant.ui.overlay.AssistantOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch overlay: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "VasuVIService"
    }
}
