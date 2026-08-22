package com.vasu.ai.core

data class VasuAudioCaptureResult(
    val success: Boolean,
    val samplesRead: Int = 0,
    val bytesRead: Int = 0,
    val reason: String = ""
)
