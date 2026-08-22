package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationManagerTest {

    @Test
    fun sensitiveAction_createsPendingRequest() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS"
        )

        assertNotNull(request)
        assertEquals(
            VasuConfirmationState.PENDING,
            manager.getState()
        )
    }

    @Test
    fun confirmation_requiresCorrectId() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS"
        )!!

        assertFalse(manager.confirm("wrong-id"))
        assertTrue(manager.confirm(request.id))

        assertEquals(
            VasuConfirmationState.CONFIRMED,
            manager.getState()
        )
    }

    @Test
    fun cancellation_clearsPendingRequest() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.PHONE_CALL,
            "Make phone call"
        )!!

        assertTrue(manager.cancel(request.id))
        assertEquals(
            VasuConfirmationState.CANCELLED,
            manager.getState()
        )
        assertEquals(null, manager.getPendingRequest())
    }

    @Test
    fun expiredConfirmation_isRejected() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS",
            now = 1_000L
        )!!

        assertFalse(
            manager.confirm(
                request.id,
                now = request.expiresAt
            )
        )

        assertEquals(
            VasuConfirmationState.EXPIRED,
            manager.getState()
        )
    }
}
