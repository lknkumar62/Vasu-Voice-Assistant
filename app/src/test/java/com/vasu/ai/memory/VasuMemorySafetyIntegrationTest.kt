package com.vasu.ai.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemorySafetyIntegrationTest {

    private val policy = VasuMemorySafetyPolicy()

    @Test
    fun ordinaryMemoryKey_isAllowed() {
        assertTrue(
            policy.isAllowedKey("favorite color")
        )
    }

    @Test
    fun sensitiveMemoryKeys_areRejected() {
        assertFalse(policy.isAllowedKey("password"))
        assertFalse(policy.isAllowedKey("PIN"))
        assertFalse(policy.isAllowedKey("OTP"))
        assertFalse(policy.isAllowedKey("API key"))
    }
}
