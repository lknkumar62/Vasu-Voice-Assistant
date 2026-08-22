package com.vasu.ai.core

class VasuWakeWordCoordinator(
    private val wakeWordManager: VasuWakeWordManager,
    private val audioLifecycleManager: VasuAudioLifecycleManager,
    private val audioCapture: VasuAudioCapture
) {

    @Synchronized
    fun start(): Boolean {
        if (
            audioLifecycleManager.getState() == VasuAudioLifecycleState.RUNNING &&
            audioCapture.isRunning()
        ) {
            return true
        }

        if (!audioLifecycleManager.start()) {
            return false
        }

        if (!audioCapture.isRunning() && !audioCapture.start()) {
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

        if (audioCapture.isRunning()) {
            audioCapture.stop()
        }

        audioCapture.release()
        audioLifecycleManager.stop()
    }

    @Synchronized
    fun onWakeWordDetected() {
        if (!wakeWordManager.onWakeWordDetected()) {
            return
        }

        wakeWordManager.beginCommandListening()
    }

    @Synchronized
    fun onCommandReceived() {
        if (!wakeWordManager.onCommandReceived()) {
            return
        }

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

        if (audioCapture.isRunning()) {
            audioCapture.stop()
        }
    }

    @Synchronized
    fun isHealthy(): Boolean {
        return audioLifecycleManager.getState() == VasuAudioLifecycleState.RUNNING &&
            audioCapture.isRunning() &&
            wakeWordManager.getWakeWordState() ==
                VasuWakeWordState.LISTENING_FOR_WAKE_WORD
    }

    fun getWakeWordState(): VasuWakeWordState =
        wakeWordManager.getWakeWordState()

    fun getAudioState(): VasuAudioLifecycleState =
        audioLifecycleManager.getState()

    fun isAudioCaptureRunning(): Boolean =
        audioCapture.isRunning()
}
