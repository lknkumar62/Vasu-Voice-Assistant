package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuWakeWordManagerTest {

    private fun manager(): VasuWakeWordManager {
        return VasuWakeWordManager(
            VasuWakeWordConfig(enabled = true)
        )
    }

    @Test
    fun start_entersWakeListening() {
        val manager = manager()

        manager.start()

        assertEquals(
            VasuWakeWordState.LISTENING_FOR_WAKE_WORD,
            manager.getState()
        )
    }

    @Test
    fun wakeWord_thenCommand_thenProcessing_followsExpectedStates() {
        val manager = manager()

        manager.start()

        assertTrue(manager.onWakeWordDetected())
        assertEquals(
            VasuWakeWordState.WAKE_DETECTED,
            manager.getState()
        )

        assertTrue(manager.beginCommandListening())
        assertEquals(
            VasuWakeWordState.COMMAND_LISTENING,
            manager.getState()
        )

        assertTrue(manager.onCommandReceived())
        assertEquals(
            VasuWakeWordState.COMMAND_RECEIVED,
            manager.getState()
        )

        assertTrue(manager.beginProcessing())
        assertEquals(
            VasuWakeWordState.PROCESSING,
            manager.getState()
        )
    }

    @Test
    fun duplicateWakeWordDuringCommand_isIgnored() {
        val manager = manager()

        manager.start()
        manager.onWakeWordDetected()
        manager.beginCommandListening()

        assertFalse(manager.onWakeWordDetected())

        assertEquals(
            VasuWakeWordState.COMMAND_LISTENING,
            manager.getState()
        )
    }

    @Test
    fun processingFinished_returnsToWakeListening() {
        val manager = manager()

        manager.start()
        manager.onWakeWordDetected()
        manager.beginCommandListening()
        manager.onCommandReceived()
        manager.beginProcessing()

        manager.returnToWakeListening()

        assertEquals(
            VasuWakeWordState.LISTENING_FOR_WAKE_WORD,
            manager.getState()
        )
    }
}
