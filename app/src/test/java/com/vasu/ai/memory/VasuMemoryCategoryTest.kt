package com.vasu.ai.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class VasuMemoryCategoryTest {

    @Test
    fun generalCategory_isAvailable() {
        assertEquals(
            VasuMemoryCategory.GENERAL,
            VasuMemoryCategory.valueOf("GENERAL")
        )
    }

    @Test
    fun preferenceCategory_isAvailable() {
        assertEquals(
            VasuMemoryCategory.PREFERENCE,
            VasuMemoryCategory.valueOf("PREFERENCE")
        )
    }
}
