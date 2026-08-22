package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationIntegrationTest {

    @Test
    fun sensitiveAction_requiresConfirmation() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.CallContact("Rahul"),
            description = "Call Rahul"
        )

        assertTrue(
            decision is VasuConfirmationDecision.RequiresConfirmation
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertNotNull(request)
        assertEquals(
            VasuConfirmationState.PENDING,
            gate.state()
        )
    }

    @Test
    fun confirmedAction_canBeAuthorizedExactlyOnce() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.SendSms(
                name = "Rahul",
                message = "Hello"
            ),
            description = "Send SMS to Rahul",
            now = 1_000L
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertTrue(gate.confirm(request.id, 1_100L))

        assertTrue(
            gate.authorizeConfirmed(
                action = VasuAction.SendSms(
                    name = "Rahul",
                    message = "Hello"
                ),
                requestId = request.id,
                now = 1_200L
            )
        )

        assertFalse(
            gate.authorizeConfirmed(
                action = VasuAction.SendSms(
                    name = "Rahul",
                    message = "Hello"
                ),
                requestId = request.id,
                now = 1_300L
            )
        )

        assertEquals(
            VasuConfirmationState.NONE,
            gate.state()
        )
    }

    @Test
    fun wrongActionCannotConsumeConfirmation() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.CallContact("Rahul"),
            description = "Call Rahul",
            now = 1_000L
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertTrue(gate.confirm(request.id, 1_100L))

        assertFalse(
            gate.authorizeConfirmed(
                action = VasuAction.SendSms(
                    name = "Rahul",
                    message = "Hello"
                ),
                requestId = request.id,
                now = 1_200L
            )
        )
    }

    @Test
    fun expiredConfirmationCannotBeConsumed() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.CallContact("Rahul"),
            description = "Call Rahul",
            now = 1_000L
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertFalse(
            gate.confirm(
                requestId = request.id,
                now = request.expiresAt
            )
        )

        assertEquals(
            VasuConfirmationState.EXPIRED,
            gate.state()
        )
    }

    @Test
    fun normalActionDoesNotRequireConfirmation() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.ClearText,
            description = "Clear text",
            now = 1_000L
        )

        assertEquals(
            VasuConfirmationDecision.Allowed,
            decision
        )

        assertEquals(
            VasuConfirmationState.NONE,
            gate.state()
        )
    }
}
