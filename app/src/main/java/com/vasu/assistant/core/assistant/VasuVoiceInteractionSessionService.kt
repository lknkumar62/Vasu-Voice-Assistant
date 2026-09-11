package com.vasu.assistant.core.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

class VasuVoiceInteractionSessionService : android.service.voice.VoiceInteractionSessionService() {

    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        Log.d(TAG, "onNewSession: Creating new voice interaction session")
        return VasuVoiceInteractionSession(this, bundle)
    }

    companion object {
        private const val TAG = "VasuVISessionService"
    }
}
