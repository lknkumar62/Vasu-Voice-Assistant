package com.vasu.ai.core

class VasuWakeWordCoordinator(
    private val wakeWordManager: VasuWakeWordManager,
    private val audioLifecycleManager: VasuAudioLifecycleManager,
    private val audioCapture: VasuAudioCapture? = null
) {
    @Synchronized
    fun start(): Boolean {
        if (!audioLifecycleManager.start()) return false

        if (audioCapture != null && !audioCapture.start()) {
            audioLifecycleManager.markRecovering()
            wakeWordManager.recover()
            return false
        }

        wakeWordManager.start()
        return true
    }

    @Synchronized
    fun stop() {
        wakeWordManager.stop()
        audioCapture?.stop()
        audioCapture?.release()
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
        audioCapture?.stop()
        wakeWordManager.recover()
    }

    fun getWakeWordState(): VasuWakeWordState = wakeWordManager.getState()

    fun getAudioState(): VasuAudioLifecycleState = audioLifecycleManager.getState()

    fun isAudioCaptureRunning(): Boolean = audioCapture?.isRunning() == true
}
