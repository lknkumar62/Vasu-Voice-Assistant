package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationSecurityContractTest {

    @Test
    fun sensitiveActions_requireConfirmation() {
        val policy = VasuConfirmationPolicy()

        assertTrue(
            policy.requiresConfirmation(
                VasuSecurityActionType.PHONE_CALL
            )
        )
        assertTrue(
            policy.requiresConfirmation(
                VasuSecurityActionType.SEND_SMS
            )
        )
    }

    @Test
    fun normalAction_doesNotRequireConfirmation() {
        assertFalse(
            VasuConfirmationPolicy().requiresConfirmation(
                VasuSecurityActionType.NORMAL
            )
        )
    }

    @Test
    fun confirmedAction_isBoundToItsExactSecurityType() {
        val manager = VasuConfirmationManager()
        val request = manager.requestConfirmation(
            actionType = VasuSecurityActionType.SEND_SMS,
            description = "Send SMS"
        )!!

        assertTrue(manager.confirm(request.id))
        assertFalse(
            manager.consumeConfirmed(
                id = request.id,
                actionType = VasuSecurityActionType.PHONE_CALL
            )
        )
        assertTrue(
            manager.consumeConfirmed(
                id = request.id,
                actionType = VasuSecurityActionType.SEND_SMS
            )
        )
    }

    @Test
    fun sensitiveActions_haveNoRetryBudget() {
        val policy = VasuRetryPolicy()

        assertEquals(
            0,
            policy.maxRetriesFor(VasuAction.SendSms("Alice", "Hello"))
        )
        assertEquals(
            0,
            policy.maxRetriesFor(VasuAction.CallContact("Alice"))
        )
    }

    @Test
    fun normalActions_keepExistingRetryBudget() {
        val policy = VasuRetryPolicy()

        assertTrue(
            policy.maxRetriesFor(VasuAction.ClearText) > 0
        )
    }
}
