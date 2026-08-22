package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryKeyNormalizerTest {

    private val normalizer = VasuMemoryKeyNormalizer()

    @Test
    fun normalize_trimsAndLowercases() {
        assertEquals(
            "my name",
            normalizer.normalize("  My Name  ")
        )
    }

    @Test
    fun normalize_collapsesWhitespace() {
        assertEquals(
            "favorite color",
            normalizer.normalize("favorite   color")
        )
    }

    @Test
    fun normalize_blankInput_returnsBlank() {
        assertTrue(
            normalizer.normalize("   ").isBlank()
        )
    }

    @Test
    fun normalize_limitsKeyLength() {
        val value = "x".repeat(500)

        assertTrue(
            normalizer.normalize(value).length <= 120
        )
    }
}
