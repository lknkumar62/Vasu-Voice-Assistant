package com.vasu.assistant.core.assistant

import android.content.Intent
import android.speech.RecognitionService

class VasuRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: android.speech.RecognitionService.Callback?) {}
    override fun onCancel(listener: android.speech.RecognitionService.Callback?) {}
    override fun onStopListening(listener: android.speech.RecognitionService.Callback?) {}
}
