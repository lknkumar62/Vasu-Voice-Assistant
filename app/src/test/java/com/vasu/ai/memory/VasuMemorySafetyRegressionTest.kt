package com.vasu.ai.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuMemorySafetyRegressionTest {

    private val policy = VasuMemorySafetyPolicy()

    @Test
    fun ordinaryPreference_isAllowed() {
        assertTrue(
            policy.isAllowedKey("preferred language")
        )
    }

    @Test
    fun credentialLikeKeys_areRejected() {
        listOf(
            "password",
            "passcode",
            "PIN",
            "OTP",
            "CVV",
            "secret",
            "token"
        ).forEach {
            assertFalse(policy.isAllowedKey(it))
        }
    }
}
