package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryPromptSectionTest {

    private val section = VasuMemoryPromptSection()

    @Test
    fun emptyMemory_returnsEmptyPromptSection() {
        val result = section.build(
            VasuMemoryConversationContext("")
        )

        assertEquals("", result)
    }

    @Test
    fun memory_isWrappedInMemoryMarkers() {
        val result = section.build(
            VasuMemoryConversationContext(
                "Known user memories:\n- name: Vasu"
            )
        )

        assertTrue(result.startsWith("[VASU_MEMORY]"))
        assertTrue(result.contains("name: Vasu"))
        assertTrue(result.endsWith("[/VASU_MEMORY]"))
    }

    @Test
    fun whitespaceOnlyMemory_returnsEmpty() {
        val result = section.build(
            VasuMemoryConversationContext("   ")
        )

        assertEquals("", result)
    }
}
