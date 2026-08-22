package com.vasu.ai.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VasuConfirmationAuditQueryTest {

    @Test
    fun recentReturnsLatestEntriesOnly() {
        val log = VasuConfirmationAuditLog(maxEntries = 10)

        repeat(5) { index ->
            log.record(
                VasuConfirmationAuditEvent(
                    eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                    actionType = null,
                    requestId = "request-$index",
                    timestamp = index.toLong()
                )
            )
        }

        val query = VasuConfirmationAuditQuery(log)

        val result = query.recent(2)

        assertEquals(2, result.size)
        assertEquals("request-3", result[0].requestId)
        assertEquals("request-4", result[1].requestId)
    }

    @Test
    fun nonPositiveLimitReturnsEmpty() {
        val log = VasuConfirmationAuditLog()

        log.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = null,
                requestId = "request-1",
                timestamp = 1L
            )
        )

        val query = VasuConfirmationAuditQuery(log)

        assertTrue(query.recent(0).isEmpty())
        assertTrue(query.recent(-1).isEmpty())
    }

    @Test
    fun snapshotIsBoundedByLogCapacity() {
        val log = VasuConfirmationAuditLog(maxEntries = 2)

        repeat(5) { index ->
            log.record(
                VasuConfirmationAuditEvent(
                    eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                    actionType = null,
                    requestId = "request-$index",
                    timestamp = index.toLong()
                )
            )
        }

        val query = VasuConfirmationAuditQuery(log)

        assertEquals(2, query.snapshot().size)
        assertEquals(2, query.size())
    }
}
