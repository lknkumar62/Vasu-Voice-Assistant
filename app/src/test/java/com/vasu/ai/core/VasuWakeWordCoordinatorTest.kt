package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuWakeWordCoordinatorTest {

    private class FakeAudioCapture : VasuAudioCapture {
        private var running = false

        override fun start(): Boolean {
            running = true
            return true
        }

        override fun read(buffer: ShortArray): VasuAudioCaptureResult {
            return VasuAudioCaptureResult(success = true)
        }

        override fun stop() {
            running = false
        }

        override fun release() {
            running = false
        }

        override fun isRunning(): Boolean = running
    }

    private fun coordinator(): VasuWakeWordCoordinator {
        val config = VasuWakeWordConfig(enabled = true)
        return VasuWakeWordCoordinator(
            wakeWordManager = VasuWakeWordManager(config),
            audioLifecycleManager = VasuAudioLifecycleManager(config),
            audioCapture = FakeAudioCapture()
        )
    }

    @Test
    fun start_initializesAudioAndWakeWord() {
        val coordinator = coordinator()

        assertTrue(coordinator.start())

        assertEquals(
            VasuAudioLifecycleState.RUNNING,
            coordinator.getAudioState()
        )
        assertEquals(
            VasuWakeWordState.LISTENING_FOR_WAKE_WORD,
            coordinator.getWakeWordState()
        )
        assertTrue(coordinator.isAudioCaptureRunning())
    }

    @Test
    fun stop_stopsWakeWordAndAudio() {
        val coordinator = coordinator()

        assertTrue(coordinator.start())
        coordinator.stop()

        assertEquals(
            VasuAudioLifecycleState.STOPPED,
            coordinator.getAudioState()
        )
        assertEquals(
            VasuWakeWordState.IDLE,
            coordinator.getWakeWordState()
        )
        assertFalse(coordinator.isAudioCaptureRunning())
    }

    @Test
    fun start_isIdempotent() {
        val coordinator = coordinator()

        assertTrue(coordinator.start())
        assertTrue(coordinator.start())

        assertEquals(
            VasuAudioLifecycleState.RUNNING,
            coordinator.getAudioState()
        )
        assertEquals(
            VasuWakeWordState.LISTENING_FOR_WAKE_WORD,
            coordinator.getWakeWordState()
        )
    }

    @Test
    fun audioFailure_entersRecovery() {
        val coordinator = coordinator()

        assertTrue(coordinator.start())
        assertTrue(coordinator.onAudioFailure())

        assertEquals(
            VasuAudioLifecycleState.RECOVERING,
            coordinator.getAudioState()
        )
        assertEquals(
            VasuWakeWordState.RECOVERING,
            coordinator.getWakeWordState()
        )
        assertFalse(coordinator.isAudioCaptureRunning())
    }
}
