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
    fun onWakeWordDetected(): Boolean {
        if (state != VasuWakeWordState.LISTENING_FOR_WAKE_WORD) {
            println("VASU_WAKEWORD_IGNORED_DURING_COMMAND")
            return false
        }
        state = VasuWakeWordState.WAKE_DETECTED
        println("VASU_WAKEWORD_DETECTED")
        return true
    }

    @Synchronized
    fun beginCommandListening(): Boolean {
        if (state != VasuWakeWordState.WAKE_DETECTED) return false
        state = VasuWakeWordState.COMMAND_LISTENING
        println("VASU_COMMAND_LISTENING")
        return true
    }

    @Synchronized
    fun onCommandReceived(): Boolean {
        if (state != VasuWakeWordState.COMMAND_LISTENING) return false
        state = VasuWakeWordState.COMMAND_RECEIVED
        return true
    }

    @Synchronized
    fun beginProcessing(): Boolean {
        if (state != VasuWakeWordState.COMMAND_RECEIVED) return false
        state = VasuWakeWordState.PROCESSING
        return true
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
