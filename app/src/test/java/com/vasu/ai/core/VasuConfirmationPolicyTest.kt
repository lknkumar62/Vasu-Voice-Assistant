package com.vasu.ai.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationPolicyTest {

    private val policy = VasuConfirmationPolicy()

    @Test
    fun normalAction_doesNotRequireConfirmation() {
        assertFalse(
            policy.requiresConfirmation(
                VasuSecurityActionType.NORMAL
            )
        )
    }

    @Test
    fun sensitiveActions_requireConfirmation() {
        assertTrue(
            policy.requiresConfirmation(
                VasuSecurityActionType.SEND_SMS
            )
        )

        assertTrue(
            policy.requiresConfirmation(
                VasuSecurityActionType.PHONE_CALL
            )
        )
    }
}
