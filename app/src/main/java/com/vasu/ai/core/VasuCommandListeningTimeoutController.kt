package com.vasu.ai.core

import android.os.Handler
import android.os.Looper

class VasuCommandListeningTimeoutController(
    private val timeoutMs: Long,
    private val onTimeout: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    @Volatile
    private var running = false

    @Synchronized
    fun start() {
        stop()
        running = true
        println("VASU_COMMAND_LISTENING_STARTED")
        handler.postDelayed({
            synchronized(this) {
                if (!running) return@synchronized
                running = false
            }
            println("VASU_COMMAND_LISTENING_TIMEOUT")
            onTimeout()
        }, timeoutMs.coerceAtLeast(1L))
    }

    @Synchronized
    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    fun isRunning(): Boolean = running
}
