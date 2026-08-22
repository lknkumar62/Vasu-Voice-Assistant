package com.vasu.ai.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemoryCleanupPolicyTest {

    @Test
    fun belowLimit_doesNotTrim() {
        val policy = VasuMemoryCleanupPolicy(100)

        assertFalse(policy.shouldTrim(50))
    }

    @Test
    fun aboveLimit_trims() {
        val policy = VasuMemoryCleanupPolicy(100)

        assertTrue(policy.shouldTrim(101))
    }

    @Test
    fun zeroLimit_isSafelyClamped() {
        val policy = VasuMemoryCleanupPolicy(0)

        assertTrue(policy.allowedEntryCount() >= 1)
    }
}
