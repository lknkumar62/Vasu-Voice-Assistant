package com.vasu.ai.core

class VasuWakeWordManager(
    private val config: VasuWakeWordConfig
) {
    @Volatile
    private var state = VasuWakeWordState.DISABLED

    @Synchronized
    fun start() {
        if (!config.enabled) {
            state = VasuWakeWordState.DISABLED
            println("VASU_WAKEWORD_DISABLED")
            return
        }
        state = VasuWakeWordState.STARTING
        println("VASU_WAKEWORD_STARTING")
        state = VasuWakeWordState.LISTENING_FOR_WAKE_WORD
        println("VASU_WAKEWORD_LISTENING")
    }

    @Synchronized
    fun stop() {
        if (state == VasuWakeWordState.DISABLED) return
        state = VasuWakeWordState.STOPPING
        println("VASU_WAKEWORD_STOPPING")
        state = VasuWakeWordState.IDLE
        println("VASU_WAKEWORD_IDLE")
    }

    @Synchronized
    fun onWakeWordDetected() {
        if (state != VasuWakeWordState.LISTENING_FOR_WAKE_WORD) return
        state = VasuWakeWordState.WAKE_DETECTED
        println("VASU_WAKEWORD_DETECTED")
    }

    @Synchronized
    fun beginCommandListening() {
        if (state != VasuWakeWordState.WAKE_DETECTED) return
        state = VasuWakeWordState.COMMAND_LISTENING
        println("VASU_COMMAND_LISTENING")
    }

    @Synchronized
    fun onCommandReceived() {
        if (state != VasuWakeWordState.COMMAND_LISTENING) return
        state = VasuWakeWordState.COMMAND_RECEIVED
    }

    @Synchronized
    fun beginProcessing() {
        if (state != VasuWakeWordState.COMMAND_RECEIVED) return
        state = VasuWakeWordState.PROCESSING
    }

    @Synchronized
    fun returnToWakeListening() {
        if (!config.enabled) {
            state = VasuWakeWordState.DISABLED
            return
        }
        state = VasuWakeWordState.LISTENING_FOR_WAKE_WORD
        println("VASU_WAKEWORD_LISTENING")
    }

    @Synchronized
    fun recover() {
        if (state == VasuWakeWordState.STOPPING) return
        state = VasuWakeWordState.RECOVERING
        println("VASU_WAKEWORD_RECOVERING")
    }

    fun getState(): VasuWakeWordState = state
}
