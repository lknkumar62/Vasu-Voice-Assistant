package com.vasu.assistant

import com.vasu.assistant.core.logging.ErrorLog
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogFormatTest {
    @Test
    fun `line contains kind message timestamp and exception head`() {
        val line = ErrorLog.formatLine("VOICE", "STT dropped", IllegalStateException("boom"))
        assertTrue(line.contains("[VOICE]"))
        assertTrue(line.contains("STT dropped"))
        assertTrue(line.contains("IllegalStateException: boom"))
        assertTrue(line.endsWith("\n"))
    }

    @Test
    fun `line without exception has no stack head`() {
        val line = ErrorLog.formatLine("COMMAND", "denied", null)
        assertTrue(line.contains("[COMMAND] denied"))
        assertTrue(!line.contains("Throwable"))
    }
}
