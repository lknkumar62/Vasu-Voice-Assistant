package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationManagerAuditIntegrationTest {

    private fun manager(): Pair<VasuConfirmationManager, VasuConfirmationLifecycle> {
        val lifecycle = VasuConfirmationLifecycle(
            VasuConfirmationAuditLog(maxEntries = 20)
        )
        val manager = VasuConfirmationManager(
            policy = VasuConfirmationPolicy(timeoutMs = 1_000L),
            lifecycle = lifecycle
        )
        return manager to lifecycle
    }

    @Test
    fun requestAndConfirmAreAudited() {
        val (manager, lifecycle) = manager()

        val request = manager.requestConfirmation(
            actionType = VasuSecurityActionType.PHONE_CALL,
            description = "Call contact",
            now = 100L
        )

        assertNotNull(request)

        val confirmed = manager.confirm(
            id = request!!.id,
            now = 200L
        )

        assertTrue(confirmed)

        val events = lifecycle.snapshot()

        assertEquals(2, events.size)
        assertEquals(
            VasuConfirmationAuditEvent.EventType.REQUESTED,
            events[0].eventType
        )
        assertEquals(
            VasuConfirmationAuditEvent.EventType.CONFIRMED,
            events[1].eventType
        )
    }

    @Test
    fun consumeIsAuditedAndIsSingleUse() {
        val (manager, lifecycle) = manager()

        val request = manager.requestConfirmation(
            actionType = VasuSecurityActionType.PHONE_CALL,
            description = "Call",
            now = 100L
        )!!

        assertTrue(manager.confirm(request.id, 200L))

        assertTrue(
            manager.consumeConfirmed(
                id = request.id,
                actionType = VasuSecurityActionType.PHONE_CALL,
                now = 300L
            )
        )

        assertFalse(
            manager.consumeConfirmed(
                id = request.id,
                actionType = VasuSecurityActionType.PHONE_CALL,
                now = 400L
            )
        )

        val events = lifecycle.snapshot()

        assertEquals(
            VasuConfirmationAuditEvent.EventType.CONSUMED,
            events.last().eventType
        )
    }

    @Test
    fun expiredRequestIsAudited() {
        val (manager, lifecycle) = manager()

        val request = manager.requestConfirmation(
            actionType = VasuSecurityActionType.SEND_SMS,
            description = "Send SMS",
            now = 100L
        )!!

        assertTrue(
            manager.expireIfNeeded(
                now = request.expiresAt
            )
        )

        assertEquals(
            VasuConfirmationState.EXPIRED,
            manager.getState()
        )

        val events = lifecycle.snapshot()

        assertEquals(
            VasuConfirmationAuditEvent.EventType.EXPIRED,
            events.last().eventType
        )
    }

    @Test
    fun wrongActionTypeIsRejectedAndAudited() {
        val (manager, lifecycle) = manager()

        val request = manager.requestConfirmation(
            actionType = VasuSecurityActionType.PHONE_CALL,
            description = "Call",
            now = 100L
        )!!

        assertTrue(manager.confirm(request.id, 200L))

        assertFalse(
            manager.consumeConfirmed(
                id = request.id,
                actionType = VasuSecurityActionType.SEND_SMS,
                now = 300L
            )
        )

        assertEquals(
            VasuConfirmationAuditEvent.EventType.REJECTED,
            lifecycle.snapshot().last().eventType
        )
    }
}
