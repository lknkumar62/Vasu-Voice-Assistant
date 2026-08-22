package com.vasu.ai.core

class VasuConfirmationLifecycle(
    private val auditLog: VasuConfirmationAuditLog =
        VasuConfirmationAuditLog()
) {

    fun onRequested(
        request: VasuConfirmationRequest,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REQUESTED,
                actionType = request.actionType,
                requestId = request.id,
                timestamp = now
            )
        )
    }

    fun onConfirmed(
        requestId: String,
        actionType: VasuSecurityActionType?,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CONFIRMED,
                actionType = actionType,
                requestId = requestId,
                timestamp = now
            )
        )
    }

    fun onCancelled(
        requestId: String,
        actionType: VasuSecurityActionType?,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CANCELLED,
                actionType = actionType,
                requestId = requestId,
                timestamp = now
            )
        )
    }

    fun onExpired(
        requestId: String?,
        actionType: VasuSecurityActionType?,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.EXPIRED,
                actionType = actionType,
                requestId = requestId,
                timestamp = now
            )
        )
    }

    fun onConsumed(
        requestId: String,
        actionType: VasuSecurityActionType,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.CONSUMED,
                actionType = actionType,
                requestId = requestId,
                timestamp = now
            )
        )
    }

    fun onRejected(
        requestId: String?,
        actionType: VasuSecurityActionType?,
        reason: String,
        now: Long = System.currentTimeMillis()
    ) {
        auditLog.record(
            VasuConfirmationAuditEvent(
                eventType = VasuConfirmationAuditEvent.EventType.REJECTED,
                actionType = actionType,
                requestId = requestId,
                timestamp = now,
                reason = reason.take(MAX_REASON_LENGTH)
            )
        )
    }

    fun snapshot(): List<VasuConfirmationAuditEvent> =
        auditLog.snapshot()

    companion object {
        const val MAX_REASON_LENGTH = 200
    }
}
