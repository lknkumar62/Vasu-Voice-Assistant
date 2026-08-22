package com.vasu.ai.core

interface VasuWakeWordDetector {
    fun start(): Boolean
    fun processAudio(pcm: ShortArray, length: Int): Boolean
    fun stop()
    fun release()
    fun isRunning(): Boolean
}
