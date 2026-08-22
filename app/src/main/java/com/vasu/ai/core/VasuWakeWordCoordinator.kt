package com.vasu.ai.core

class VasuWakeWordCoordinator(
    private val wakeWordManager: VasuWakeWordManager,
    private val audioLifecycleManager: VasuAudioLifecycleManager
) {
    @Synchronized
    fun start() {
        if (!audioLifecycleManager.start()) return
        wakeWordManager.start()
    }

    @Synchronized
    fun stop() {
        wakeWordManager.stop()
        audioLifecycleManager.stop()
    }

    @Synchronized
    fun onWakeWordDetected() {
        wakeWordManager.onWakeWordDetected()
        wakeWordManager.beginCommandListening()
    }

    @Synchronized
    fun onCommandReceived() {
        wakeWordManager.onCommandReceived()
        wakeWordManager.beginProcessing()
    }

    @Synchronized
    fun onProcessingFinished() {
        wakeWordManager.returnToWakeListening()
    }

    @Synchronized
    fun onAudioFailure() {
        audioLifecycleManager.markRecovering()
        wakeWordManager.recover()
    }

    fun getWakeWordState(): VasuWakeWordState = wakeWordManager.getState()

    fun getAudioState(): VasuAudioLifecycleState = audioLifecycleManager.getState()
}
