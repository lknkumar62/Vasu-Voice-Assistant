package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Test

class VasuConfirmationAuditSummaryTest {

    @Test
    fun summaryCountsAllEventTypes() {
        val events = listOf(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = VasuSecurityActionType.PHONE_CALL,
                requestId = "1",
                timestamp = 1L
            ),
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CONFIRMED,
                actionType = VasuSecurityActionType.PHONE_CALL,
                requestId = "1",
                timestamp = 2L
            ),
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CONSUMED,
                actionType = VasuSecurityActionType.PHONE_CALL,
                requestId = "1",
                timestamp = 3L
            ),
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REJECTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "2",
                timestamp = 4L,
                reason = "wrong_action"
            )
        )

        val summary = VasuConfirmationAuditSummary.from(events)

        assertEquals(4, summary.total)
        assertEquals(1, summary.requested)
        assertEquals(1, summary.confirmed)
        assertEquals(0, summary.cancelled)
        assertEquals(0, summary.expired)
        assertEquals(1, summary.consumed)
        assertEquals(1, summary.rejected)
    }

    @Test
    fun emptyEventsProduceZeroSummary() {
        val summary = VasuConfirmationAuditSummary.from(emptyList())

        assertEquals(0, summary.total)
        assertEquals(0, summary.requested)
        assertEquals(0, summary.confirmed)
        assertEquals(0, summary.cancelled)
        assertEquals(0, summary.expired)
        assertEquals(0, summary.consumed)
        assertEquals(0, summary.rejected)
    }
}
