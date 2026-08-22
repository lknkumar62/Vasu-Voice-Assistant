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
}
