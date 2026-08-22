package com.vasu.ai.core

class VasuAudioLifecycleManager(
    private val config: VasuWakeWordConfig
) {
    @Volatile
    private var state = VasuAudioLifecycleState.STOPPED

    @Synchronized
    fun start(): Boolean {
        if (!config.enabled) {
            state = VasuAudioLifecycleState.STOPPED
            return false
        }
        if (
            state == VasuAudioLifecycleState.RUNNING ||
            state == VasuAudioLifecycleState.STARTING
        ) {
            return true
        }
        state = VasuAudioLifecycleState.STARTING
        println("VASU_AUDIO_STARTING")
        // Actual microphone initialization is intentionally deferred.
        state = VasuAudioLifecycleState.RUNNING
        println("VASU_AUDIO_RUNNING")
        return true
    }

    @Synchronized
    fun stop() {
        if (state == VasuAudioLifecycleState.STOPPED) return
        state = VasuAudioLifecycleState.STOPPING
        println("VASU_AUDIO_STOPPING")
        state = VasuAudioLifecycleState.STOPPED
        println("VASU_AUDIO_STOPPED")
    }

    @Synchronized
    fun markRecovering() {
        state = VasuAudioLifecycleState.RECOVERING
        println("VASU_AUDIO_RECOVERING")
    }

    fun getState(): VasuAudioLifecycleState = state
}
