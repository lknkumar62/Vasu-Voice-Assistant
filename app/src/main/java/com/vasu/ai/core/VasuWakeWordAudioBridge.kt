package com.vasu.ai.core

class VasuWakeWordAudioBridge(
    private val audioCapture: VasuAudioCaptureManager,
    private val detector: VasuWakeWordDetector,
    private val coordinator: VasuWakeWordCoordinator,
    private val onCommandListeningRequested: () -> Unit = {}
) {
    @Synchronized
    fun start(): Boolean {
        if (!detector.start()) return false

        val captureStarted = audioCapture.startCaptureLoop { pcm, length ->
            if (!detector.isRunning()) return@startCaptureLoop

            val detected = detector.processAudio(pcm, length)
            if (detected) {
                audioCapture.stop()
                detector.stop()
                coordinator.onWakeWordDetected()
                onCommandListeningRequested()
            }
        }

        if (!captureStarted) {
            detector.stop()
            return false
        }

        println("VASU_WAKEWORD_ENGINE_RUNNING")
        return true
    }

    @Synchronized
    fun stop() {
        audioCapture.stop()
        detector.stop()
    }

    @Synchronized
    fun release() {
        audioCapture.release()
        detector.release()
    }

    fun isRunning(): Boolean =
        audioCapture.isRunning() && detector.isRunning()
}
