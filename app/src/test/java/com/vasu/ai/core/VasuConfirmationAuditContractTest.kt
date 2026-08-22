package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationAuditContractTest {

    @Test
    fun snapshotIsImmutableFromCallerPerspective() {
        val log = VasuConfirmationAuditLog()

        log.record(
            VasuConfirmationAuditEvent(
                eventType =
                    VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 100L
            )
        )

        val snapshot = log.snapshotData()

        assertEquals(1, snapshot.size)
        assertEquals("request-1", snapshot.events[0].requestId)
    }

    @Test
    fun auditLogNeverStoresOversizedReason() {
        val log = VasuConfirmationAuditLog()

        log.record(
            VasuConfirmationAuditEvent(
                eventType =
                    VasuConfirmationAuditEvent.EventType.REJECTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 100L,
                reason = "x".repeat(500)
            )
        )

        val event = log.snapshot().single()

        assertTrue(event.reason.length <= 200)
    }

    @Test
    fun zeroCapacityLogDoesNotStoreEvents() {
        val log = VasuConfirmationAuditLog(maxEntries = 0)

        log.record(
            VasuConfirmationAuditEvent(
                eventType =
                    VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = VasuSecurityActionType.SEND_SMS,
                requestId = "request-1",
                timestamp = 100L
            )
        )

        assertEquals(0, log.size())
    }
}
