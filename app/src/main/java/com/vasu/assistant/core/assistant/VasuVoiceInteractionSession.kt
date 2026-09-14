package com.vasu.assistant.core.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class VasuVoiceInteractionSession(
    private val context: Context,
    private val bundle: Bundle?
) : VoiceInteractionSession(context) {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "onShow: showFlags=$showFlags")
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        Log.d(TAG, "onHandleAssist")
        launchOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
    }

    private fun launchOverlay() {
        try {
            val intent = Intent(context, com.vasu.assistant.ui.overlay.AssistantOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch overlay: ${e.message}")
        }
    }

    fun handleUserQuery(query: String) {
        Log.d(TAG, "handleUserQuery: $query")
    }

    companion object {
        private const val TAG = "VasuVISession"
    }
}