package com.vasu.assistant

import com.vasu.assistant.ui.chat.ChatMessage
import com.vasu.assistant.ui.chat.ChatTextFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextFormatterTest {

    @Test
    fun deriveTitleCollapsesWhitespace() {
        assertEquals("Hello VASU", ChatTextFormatter.deriveTitle("  Hello   VASU  "))
    }

    @Test
    fun deriveTitleTruncatesLongMessages() {
        val title = ChatTextFormatter.deriveTitle("a".repeat(60))
        assertEquals(41, title.length)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun deriveTitleFallsBackForBlank() {
        assertEquals("New chat", ChatTextFormatter.deriveTitle("   "))
    }

    @Test
    fun plainTextFormatsBothSpeakers() {
        val out = ChatTextFormatter.toPlainText(
            listOf(
                ChatMessage(id = "1", content = "hi", isUser = true),
                ChatMessage(id = "2", content = "hello", isUser = false)
            )
        )
        assertTrue(out.contains("[You] hi"))
        assertTrue(out.contains("[VASU] hello"))
    }

    @Test
    fun plainTextHandlesEmptyList() {
        assertTrue(ChatTextFormatter.toPlainText(emptyList()).isNotEmpty())
    }

    @Test
    fun plainTextLabelsToolMessages() {
        val out = ChatTextFormatter.toPlainText(
            listOf(
                ChatMessage(
                    id = "3",
                    content = "Torch on kar diya",
                    isUser = false,
                    isToolExecution = true,
                    toolName = "torch_on"
                )
            )
        )
        assertTrue(out.contains("tool: torch_on"))
    }
}
