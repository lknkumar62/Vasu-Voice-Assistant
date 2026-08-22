package com.vasu.ai.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationContractTest {

    @Test
    fun pendingConfirmation_isNotExecutionAuthorization() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.CallContact("Boss"),
            description = "Call Boss",
            now = 1_000L
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertFalse(
            gate.authorizeConfirmed(
                action = VasuAction.CallContact("Boss"),
                requestId = request.id,
                now = 1_100L
            )
        )
    }

    @Test
    fun confirmation_isSingleUse() {
        val gate = VasuConfirmationGate()

        val decision = gate.evaluate(
            action = VasuAction.CallContact("Boss"),
            description = "Call Boss",
            now = 1_000L
        )

        val request =
            (decision as VasuConfirmationDecision.RequiresConfirmation).request

        assertTrue(gate.confirm(request.id, 1_100L))

        assertTrue(
            gate.authorizeConfirmed(
                VasuAction.CallContact("Boss"),
                request.id,
                1_200L
            )
        )

        assertFalse(
            gate.authorizeConfirmed(
                VasuAction.CallContact("Boss"),
                request.id,
                1_300L
            )
        )
    }
}
