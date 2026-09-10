package com.vasu.assistant.voice

import android.content.Context

/**
 * GeminiAudioPlayer delegating to NativeAudioPlayer.
 * Inherits the rigid state machine (IDLE, PLAYING, DRAINING),
 * onEnd callback support, and accurate audio draining behavior.
 */
class GeminiAudioPlayer(context: Context) : NativeAudioPlayer(context)
