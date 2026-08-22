package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryContextFormatterTest {

    private val formatter = VasuMemoryContextFormatter()

    @Test
    fun emptySnapshot_returnsEmptyString() {
        assertEquals(
            "",
            formatter.format(VasuMemorySnapshot(emptyList()))
        )
    }

    @Test
    fun snapshot_containsMemoryValues() {
        val snapshot = VasuMemorySnapshot(
            listOf(
                VasuMemoryEntry(
                    key = "name",
                    value = "Vasu"
                )
            )
        )

        val result = formatter.format(snapshot)

        assertTrue(result.contains("name"))
        assertTrue(result.contains("Vasu"))
    }

    @Test
    fun snapshot_respectsMaximumEntries() {
        val snapshot = VasuMemorySnapshot(
            (1..20).map {
                VasuMemoryEntry(
                    key = "key$it",
                    value = "value$it"
                )
            }
        )

        val result = formatter.format(snapshot, 3)

        assertTrue(result.contains("key1"))
        assertTrue(result.contains("key3"))
        assertFalse(result.contains("key4"))
    }
}
