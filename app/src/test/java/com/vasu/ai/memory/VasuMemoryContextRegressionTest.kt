package com.vasu.ai.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryContextRegressionTest {

    @Test
    fun contextEntryLimit_isBounded() {
        assertTrue(
            VasuMemoryBoundary.MAX_CONTEXT_ENTRIES > 0
        )
    }

    @Test
    fun keyAndValueLimits_areBounded() {
        assertTrue(
            VasuMemoryBoundary.MAX_KEY_LENGTH <= 120
        )

        assertTrue(
            VasuMemoryBoundary.MAX_VALUE_LENGTH <= 1000
        )
    }
}
