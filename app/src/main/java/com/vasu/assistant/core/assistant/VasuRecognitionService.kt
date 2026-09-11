package com.vasu.assistant.core.assistant

import android.content.Intent
import android.speech.RecognitionService
import android.util.Log

class VasuRecognitionService : RecognitionService() {

    override fun onCancel(callback: Callback) {
        Log.d(TAG, "onCancel")
    }

    override fun onStartListening(intent: Intent?, callback: Callback?) {
        Log.d(TAG, "onStartListening: Redirecting to VASU pipeline")
        try {
            callback?.error(8) // ERROR_CLIENT
        } catch (e: Exception) {
            Log.e(TAG, "onStartListening error: ${e.message}")
        }
    }

    override fun onStopListening(callback: Callback?) {
        Log.d(TAG, "onStopListening")
    }

    companion object {
        private const val TAG = "VasuRecognition"
    }
}
