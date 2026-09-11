package com.vasu.assistant.core.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

class VasuVoiceInteractionSessionServiceImpl : VoiceInteractionSessionService() {

    override fun onNewSession(bundle: Bundle?): VoiceInteractionSession {
        Log.d(TAG, "onNewSession: Creating session")
        return VasuVoiceInteractionSession(this, bundle)
    }

    companion object {
        private const val TAG = "VUVISessionService"
    }
}
