package com.vasu.assistant.core.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.View
import android.view.WindowManager

class VasuVoiceInteractionSession(
    private val context: Context,
    private val bundle: Bundle?
) : VoiceInteractionSession(context) {

    override fun onCreate(): Unit = Log.d(TAG, "onCreate").let { }

    override fun onStart(p0: Intent?, p1: Int): Unit = Log.d(TAG, "onStart").let { }

    override fun onReady(): Unit = Log.d(TAG, "onReady").let { }

    override fun onFinish(): Unit = Log.d(TAG, "onFinish").let { }

    override fun onDestroy(): Unit = Log.d(TAG, "onDestroy").let { }

    override fun onAssist(
        data: Bundle?,
        assistContext: Bundle?,
        state: Int,
        activityId: Int
    ): Bundle? {
        Log.d(TAG, "onAssist: state=$state, activityId=$activityId")
        launchOverlay()
        return null
    }

    override fun onHandleAssist(data: Bundle?, activityId: Int) {
        Log.d(TAG, "onHandleAssist: activityId=$activityId")
        launchOverlay()
    }

    override fun onHandleAssistRequest(data: Bundle?, resultReceiver: ResultReceiver?) {
        Log.d(TAG, "onHandleAssistRequest")
        launchOverlay()
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
