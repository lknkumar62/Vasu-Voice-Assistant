package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationAuditLogTest {

    @Test
    fun recordsEventsInOrder() {
        val log = VasuConfirmationAuditLog()

        log.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 100L
            )
        )

        log.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CONFIRMED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 110L
            )
        )

        val events = log.snapshot()

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
    fun keepsOnlyConfiguredMaximumEntries() {
        val log = VasuConfirmationAuditLog(maxEntries = 2)

        repeat(3) { index ->
            log.record(
                VasuConfirmationAuditEvent(
                    eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                    actionType = VasuSecurityActionType.SEND_SMS,
                    requestId = "request-$index",
                    timestamp = index.toLong()
                )
            )
        }

        val events = log.snapshot()

        assertEquals(2, events.size)
        assertEquals("request-1", events[0].requestId)
        assertEquals("request-2", events[1].requestId)
    }

    @Test
    fun clearRemovesAllEvents() {
        val log = VasuConfirmationAuditLog()

        log.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REJECTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 100L
            )
        )

        log.clear()

        assertTrue(log.snapshot().isEmpty())
    }
}
