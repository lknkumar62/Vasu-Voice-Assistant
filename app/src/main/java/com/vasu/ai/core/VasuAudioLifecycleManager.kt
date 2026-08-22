package com.vasu.ai.core

class VasuAudioLifecycleManager(
    private val config: VasuWakeWordConfig
) {
    @Volatile
    private var state = VasuAudioLifecycleState.STOPPED

    @Volatile
    private var recoveryAttempts = 0

    @Synchronized
    fun start(): Boolean {
        if (!config.enabled) {
            state = VasuAudioLifecycleState.STOPPED
            recoveryAttempts = 0
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

        state = VasuAudioLifecycleState.RUNNING
        println("VASU_AUDIO_RUNNING")

        return true
    }

    @Synchronized
    fun stop() {
        if (state == VasuAudioLifecycleState.STOPPED) {
            recoveryAttempts = 0
            return
        }

        state = VasuAudioLifecycleState.STOPPING
        println("VASU_AUDIO_STOPPING")

        state = VasuAudioLifecycleState.STOPPED
        recoveryAttempts = 0

        println("VASU_AUDIO_STOPPED")
    }

    @Synchronized
    fun markRecovering(): Boolean {
        if (!config.enabled) {
            state = VasuAudioLifecycleState.STOPPED
            return false
        }

        if (state == VasuAudioLifecycleState.STOPPING) {
            return false
        }

        val maxAttempts = config.maxRecoveryAttempts.coerceAtLeast(1)

        if (recoveryAttempts >= maxAttempts) {
            state = VasuAudioLifecycleState.STOPPED
            println("VASU_AUDIO_RECOVERY_EXHAUSTED")
            return false
        }

        recoveryAttempts++

        state = VasuAudioLifecycleState.RECOVERING

        println(
            "VASU_AUDIO_RECOVERING " +
                "attempt=$recoveryAttempts/$maxAttempts"
        )

        return true
    }

    @Synchronized
    fun completeRecovery(): Boolean {
        if (state != VasuAudioLifecycleState.RECOVERING) {
            return false
        }

        state = VasuAudioLifecycleState.RUNNING

        println("VASU_AUDIO_RECOVERY_COMPLETE")

        return true
    }

    @Synchronized
    fun resetRecoveryAttempts() {
        recoveryAttempts = 0
    }

    fun getRecoveryAttempts(): Int =
        recoveryAttempts

    fun canRecover(): Boolean =
        recoveryAttempts < config.maxRecoveryAttempts.coerceAtLeast(1)

    fun getState(): VasuAudioLifecycleState =
        state
}
