package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuAudioLifecycleManagerTest {

    private fun manager(): VasuAudioLifecycleManager =
        VasuAudioLifecycleManager(VasuWakeWordConfig(enabled = true))

    @Test
    fun start_entersRunning() {
        val manager = manager()
        assertTrue(manager.start())
        assertEquals(VasuAudioLifecycleState.RUNNING, manager.getState())
    }

    @Test
    fun repeatedStart_isIdempotent() {
        val manager = manager()
        assertTrue(manager.start())
        assertTrue(manager.start())
        assertEquals(VasuAudioLifecycleState.RUNNING, manager.getState())
    }

    @Test
    fun stop_returnsToStopped() {
        val manager = manager()
        manager.start()
        manager.stop()
        assertEquals(VasuAudioLifecycleState.STOPPED, manager.getState())
    }

    @Test
    fun disabledConfiguration_cannotStart() {
        val manager = VasuAudioLifecycleManager(VasuWakeWordConfig(enabled = false))
        assertFalse(manager.start())
        assertEquals(VasuAudioLifecycleState.STOPPED, manager.getState())
    }

    @Test
    fun recoveryAttempts_areBounded() {
        val config = VasuWakeWordConfig(enabled = true, maxRecoveryAttempts = 3)
        val manager = VasuAudioLifecycleManager(config)
        assertTrue(manager.start())

        var acceptedRecoveries = 0
        repeat(20) {
            if (manager.markRecovering()) acceptedRecoveries++
        }

        assertEquals(config.maxRecoveryAttempts, acceptedRecoveries)
        assertEquals(config.maxRecoveryAttempts, manager.getRecoveryAttempts())
        assertFalse(manager.canRecover())
    }

    @Test
    fun completeRecovery_returnsToRunning() {
        val manager = manager()
        manager.start()
        assertTrue(manager.markRecovering())
        assertTrue(manager.completeRecovery())
        assertEquals(VasuAudioLifecycleState.RUNNING, manager.getState())
    }

    @Test
    fun resetRecoveryAttempts_clearsRecoveryBudget() {
        val manager = manager()
        manager.start()
        assertTrue(manager.markRecovering())
        manager.resetRecoveryAttempts()
        assertEquals(0, manager.getRecoveryAttempts())
        assertTrue(manager.canRecover())
    }

    @Test
    fun markRecovering_entersRecoveringState() {
        val manager = manager()
        manager.start()
        assertTrue(manager.markRecovering())
        assertEquals(VasuAudioLifecycleState.RECOVERING, manager.getState())
    }

    @Test
    fun recoveryExhaustion_doesNotExceedConfiguredMaximum() {
        val config = VasuWakeWordConfig(enabled = true)
        val manager = VasuAudioLifecycleManager(config)
        assertTrue(manager.start())

        var accepted = 0
        repeat(config.maxRecoveryAttempts + 5) {
            if (manager.markRecovering()) {
                accepted++
                manager.completeRecovery()
            }
        }

        assertTrue(accepted <= config.maxRecoveryAttempts)
    }

    @Test
    fun resetAfterRecovery_allowsFreshRecoveryBudget() {
        val config = VasuWakeWordConfig(enabled = true)
        val manager = VasuAudioLifecycleManager(config)
        assertTrue(manager.start())

        repeat(config.maxRecoveryAttempts) {
            assertTrue(manager.markRecovering())
            assertTrue(manager.completeRecovery())
        }

        manager.resetRecoveryAttempts()
        assertTrue(manager.canRecover())
        assertTrue(manager.markRecovering())
    }
}
