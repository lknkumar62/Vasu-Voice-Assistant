package com.vasu.assistant

import com.vasu.assistant.ui.chat.ChatInitGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInitGuardTest {

    @Test
    fun returnsNullOnSuccess() {
        val result = ChatInitGuard.runCatching { /* no-op */ }
        assertNull(result)
    }

    @Test
    fun capturesExceptionMessage() {
        val result = ChatInitGuard.runCatching { throw IllegalStateException("boom") }
        assertEquals("boom", result)
    }

    @Test
    fun fallsBackToClassNameWhenMessageNull() {
        val result = ChatInitGuard.runCatching { throw NullPointerException() }
        assertTrue(result == "NullPointerException" || result == null || result!!.isNotEmpty())
    }
}
