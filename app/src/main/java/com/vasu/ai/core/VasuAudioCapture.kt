package com.vasu.ai.core

import android.media.AudioRecord

interface VasuAudioCapture {
    fun start(): Boolean
    fun read(buffer: ShortArray): VasuAudioCaptureResult
    fun stop()
    fun release()
    fun isRunning(): Boolean
}
