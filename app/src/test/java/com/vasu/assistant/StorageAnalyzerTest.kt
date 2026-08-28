package com.vasu.assistant

import com.vasu.assistant.core.automation.Mission
import com.vasu.assistant.core.automation.MissionStatus
import com.vasu.assistant.core.automation.MissionStep
import org.junit.Assert.*
import org.junit.Test

class StorageAnalyzerTest {

    @Test
    fun `mission should initialize with CREATED status`() {
        val mission = Mission(name = "Test Mission", steps = listOf(MissionStep("open_app")))
        assertEquals(MissionStatus.CREATED, mission.status)
        assertEquals("Test Mission", mission.name)
        assertEquals(1, mission.steps.size)
        assertEquals(0, mission.currentStep)
    }

    @Test
    fun `mission steps should have default values`() {
        val step = MissionStep(action = "click", parameters = mapOf("text" to "OK"))
        assertEquals("click", step.action)
        assertEquals("OK", step.parameters["text"])
        assertEquals(10000L, step.timeout)
        assertEquals(2, step.retryCount)
        assertFalse(step.requireConfirmation)
    }

    @Test
    fun `smart mode enum should have all modes`() {
        val modes = com.vasu.assistant.maps.SmartModeManager.SmartMode.values()
        assertEquals(6, modes.size)
        assertTrue(modes.contains(com.vasu.assistant.maps.SmartModeManager.SmartMode.DRIVING))
        assertTrue(modes.contains(com.vasu.assistant.maps.SmartModeManager.SmartMode.SLEEP))
    }
}
