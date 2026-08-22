package com.vasu.ai.core

class VasuConfirmationAuditQuery(
    private val auditLog: VasuConfirmationAuditLog
) {

    fun snapshot(): VasuConfirmationAuditSnapshot =
        auditLog.snapshotData()

    fun recent(limit: Int): List<VasuConfirmationAuditEvent> {
        if (limit <= 0) return emptyList()

        return auditLog
            .snapshot()
            .takeLast(limit)
    }

    fun size(): Int = auditLog.size()

    fun byEventType(
        eventType: VasuConfirmationAuditEvent.EventType
    ): List<VasuConfirmationAuditEvent> =
        VasuConfirmationAuditFilter.byEventType(
            events = auditLog.snapshot(),
            eventType = eventType
        )

    fun byActionType(
        actionType: VasuSecurityActionType
    ): List<VasuConfirmationAuditEvent> =
        VasuConfirmationAuditFilter.byActionType(
            events = auditLog.snapshot(),
            actionType = actionType
        )

    fun rejected(): List<VasuConfirmationAuditEvent> =
        VasuConfirmationAuditFilter.rejected(
            auditLog.snapshot()
        )
}
