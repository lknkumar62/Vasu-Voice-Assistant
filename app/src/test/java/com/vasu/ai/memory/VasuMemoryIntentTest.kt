package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class VasuMemoryIntentTest {

    @Test
    fun rememberIntent_preservesKeyAndValue() {
        val intent = VasuMemoryIntent.Remember(
            key = "name",
            value = "Vasu"
        )

        assertEquals("name", intent.key)
        assertEquals("Vasu", intent.value)
    }

    @Test
    fun recallIntent_preservesKey() {
        val intent = VasuMemoryIntent.Recall("name")

        assertEquals("name", intent.key)
    }

    @Test
    fun forgetIntent_preservesKey() {
        val intent = VasuMemoryIntent.Forget("name")

        assertEquals("name", intent.key)
    }
}
