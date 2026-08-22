package com.vasu.ai.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemorySafetyPolicyTest {

    private val policy = VasuMemorySafetyPolicy()

    @Test
    fun normalKey_isAllowed() {
        assertTrue(
            policy.isAllowedKey("favorite color")
        )
    }

    @Test
    fun password_isBlocked() {
        assertFalse(
            policy.isAllowedKey("password")
        )
    }

    @Test
    fun apiKey_isBlocked() {
        assertFalse(
            policy.isAllowedKey("API key")
        )
    }

    @Test
    fun blankKey_isBlocked() {
        assertFalse(
            policy.isAllowedKey("   ")
        )
    }
}
