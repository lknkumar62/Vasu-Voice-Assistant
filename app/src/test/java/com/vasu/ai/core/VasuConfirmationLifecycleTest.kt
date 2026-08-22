package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationLifecycleTest {

    @Test
    fun recordsRequestLifecycle() {
        val lifecycle = VasuConfirmationLifecycle()

        val request = VasuConfirmationRequest(
            id = "request-1",
            actionType = VasuSecurityActionType.SEND_SMS,
            description = "Send message",
            createdAt = 100L,
            expiresAt = 10_000L
        )

        lifecycle.onRequested(request, 100L)
        lifecycle.onConfirmed(
            requestId = request.id,
            actionType = request.actionType,
            now = 110L
        )
        lifecycle.onConsumed(
            requestId = request.id,
            actionType = request.actionType,
            now = 120L
        )

        val events = lifecycle.snapshot()

        assertEquals(3, events.size)
        assertEquals(
            VasuConfirmationAuditEvent.EventType.REQUESTED,
            events[0].eventType
        )
        assertEquals(
            VasuConfirmationAuditEvent.EventType.CONFIRMED,
            events[1].eventType
        )
        assertEquals(
            VasuConfirmationAuditEvent.EventType.CONSUMED,
            events[2].eventType
        )
    }

    @Test
    fun rejectionReasonIsBounded() {
        val lifecycle = VasuConfirmationLifecycle()

        lifecycle.onRejected(
            requestId = "request-1",
            actionType = VasuSecurityActionType.SEND_SMS,
            reason = "x".repeat(500),
            now = 100L
        )

        val event = lifecycle.snapshot().single()

        assertEquals(
            VasuConfirmationAuditEvent.EventType.REJECTED,
            event.eventType
        )
        assertTrue(event.reason.length <= 200)
    }

    @Test
    fun expiryIsRecorded() {
        val lifecycle = VasuConfirmationLifecycle()

        lifecycle.onExpired(
            requestId = "request-1",
            actionType = VasuSecurityActionType.SEND_SMS,
            now = 200L
        )

        val event = lifecycle.snapshot().single()

        assertEquals(
            VasuConfirmationAuditEvent.EventType.EXPIRED,
            event.eventType
        )
        assertEquals("request-1", event.requestId)
    }
}
