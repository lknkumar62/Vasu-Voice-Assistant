package com.vasu.assistant

import com.vasu.assistant.core.automation.ActionResult
import org.junit.Assert.*
import org.junit.Test

class ActionResultTest {

    @Test
    fun `success result should have correct fields`() {
        val result = ActionResult.success("test", "Success message", mapOf("key" to "value"))
        assertTrue(result.success)
        assertEquals("test", result.action)
        assertEquals("Success message", result.message)
        assertNull(result.error)
        assertEquals("value", result.data?.get("key"))
    }

    @Test
    fun `error result should have correct fields`() {
        val result = ActionResult.error("test", "Error message", "Details")
        assertFalse(result.success)
        assertEquals("test", result.action)
        assertEquals("Error message", result.message)
        assertEquals("Details", result.error)
    }

    @Test
    fun `result should have timestamp`() {
        val before = System.currentTimeMillis()
        val result = ActionResult.success("test", "msg")
        val after = System.currentTimeMillis()
        assertTrue(result.timestamp in before..after)
    }
}
