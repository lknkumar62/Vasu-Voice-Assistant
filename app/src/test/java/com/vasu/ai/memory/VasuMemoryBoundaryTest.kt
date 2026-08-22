package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class VasuMemoryBoundaryTest {

    @Test
    fun key_isLimitedTo120Characters() {
        assertEquals(
            120,
            VasuMemoryBoundary.safeKey("a".repeat(200)).length
        )
    }

    @Test
    fun value_isLimitedTo1000Characters() {
        assertEquals(
            1000,
            VasuMemoryBoundary.safeValue("a".repeat(2000)).length
        )
    }

    @Test
    fun keyWhitespace_isNormalized() {
        assertEquals(
            "favorite color",
            VasuMemoryBoundary.safeKey("  Favorite   Color  ")
        )
    }
}
