package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationGateTest {

    @Test
    fun normalAction_isAllowed() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.ClearText

        val result = gate.evaluate(
            action = action,
            description = "Normal action"
        )

        assertTrue(result is VasuConfirmationDecision.Allowed)
    }

    @Test
    fun sensitiveAction_requiresConfirmation() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.SendSms(
            name = "Alice",
            message = "Hello"
        )

        val result = gate.evaluate(
            action = action,
            description = "Send SMS"
        )

        assertTrue(
            result is VasuConfirmationDecision.RequiresConfirmation
        )

        assertEquals(
            VasuConfirmationState.PENDING,
            gate.state()
        )
    }

    @Test
    fun wrongConfirmationId_isRejected() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.SendSms(
            name = "Alice",
            message = "Hello"
        )

        gate.evaluate(
            action = action,
            description = "Send SMS"
        )

        assertFalse(gate.confirm("invalid-id"))
    }

    @Test
    fun cancellation_clearsPendingRequest() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.CallContact("Alice")

        val result = gate.evaluate(
            action = action,
            description = "Make phone call"
        )

        val request =
            (result as VasuConfirmationDecision.RequiresConfirmation)
                .request

        assertTrue(gate.cancel(request.id))
        assertEquals(
            VasuConfirmationState.CANCELLED,
            gate.state()
        )
    }

    @Test
    fun confirmedRequest_matchesSameSensitiveAction() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.SendSms(
            name = "Alice",
            message = "Hello"
        )

        val result = gate.evaluate(
            action = action,
            description = "Send SMS"
        ) as VasuConfirmationDecision.RequiresConfirmation

        assertTrue(gate.confirm(result.request.id))
        assertTrue(
            gate.authorizeConfirmed(
                action = action,
                requestId = result.request.id
            )
        )
    }

    @Test
    fun confirmedRequest_cannotAuthorizeDifferentSensitiveAction() {
        val gate = VasuConfirmationGate()

        val smsAction = VasuAction.SendSms(
            name = "Alice",
            message = "Hello"
        )
        val callAction = VasuAction.CallContact("Alice")

        val result = gate.evaluate(
            action = smsAction,
            description = "Send SMS"
        ) as VasuConfirmationDecision.RequiresConfirmation

        assertTrue(gate.confirm(result.request.id))
        assertFalse(
            gate.authorizeConfirmed(
                action = callAction,
                requestId = result.request.id
            )
        )

        assertTrue(
            gate.authorizeConfirmed(
                action = smsAction,
                requestId = result.request.id
            )
        )
    }

    @Test
    fun expiredRequest_cannotAuthorizeAction() {
        val gate = VasuConfirmationGate()

        val action = VasuAction.SendSms(
            name = "Alice",
            message = "Hello"
        )

        val result = gate.evaluate(
            action = action,
            description = "Send SMS",
            now = 1_000L
        ) as VasuConfirmationDecision.RequiresConfirmation

        assertTrue(
            gate.confirm(
                result.request.id,
                now = 1_000L
            )
        )

        assertFalse(
            gate.authorizeConfirmed(
                action = action,
                requestId = result.request.id,
                now = result.request.expiresAt
            )
        )

        assertEquals(
            VasuConfirmationState.EXPIRED,
            gate.state()
        )
    }
}
