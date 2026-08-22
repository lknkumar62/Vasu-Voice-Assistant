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
    fun duplicatePendingRequest_reusesSameRequestForSameAction() {
        val manager = VasuConfirmationManager()

        val first = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS"
        )!!

        val second = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send another SMS"
        )!!

        assertEquals(first.id, second.id)
        assertEquals(
            VasuConfirmationState.PENDING,
            manager.getState()
        )
    }

    @Test
    fun duplicatePendingRequest_forDifferentAction_isRejected() {
        val manager = VasuConfirmationManager()

        assertNotNull(
            manager.requestConfirmation(
                VasuSecurityActionType.SEND_SMS,
                "Send SMS"
            )
        )

        assertEquals(
            null,
            manager.requestConfirmation(
                VasuSecurityActionType.PHONE_CALL,
                "Make phone call"
            )
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
    fun confirmedRequest_isConsumedOnce() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS"
        )!!

        assertTrue(manager.confirm(request.id))
        assertTrue(
            manager.consumeConfirmed(
                request.id,
                VasuSecurityActionType.SEND_SMS
            )
        )

        assertFalse(
            manager.consumeConfirmed(
                request.id,
                VasuSecurityActionType.SEND_SMS
            )
        )

        assertEquals(
            VasuConfirmationState.NONE,
            manager.getState()
        )
        assertEquals(null, manager.getPendingRequest())
    }

    @Test
    fun confirmedRequest_cannotBeConsumedForDifferentAction() {
        val manager = VasuConfirmationManager()

        val request = manager.requestConfirmation(
            VasuSecurityActionType.SEND_SMS,
            "Send SMS"
        )!!

        assertTrue(manager.confirm(request.id))

        assertFalse(
            manager.consumeConfirmed(
                request.id,
                VasuSecurityActionType.PHONE_CALL
            )
        )

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
    fun expiredConfirmation_isRejectedAndCleaned() {
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
        assertEquals(null, manager.getPendingRequest())
    }
}
