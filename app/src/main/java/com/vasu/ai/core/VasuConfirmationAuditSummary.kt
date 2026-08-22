package com.vasu.ai.core

data class VasuConfirmationAuditSummary(
    val total: Int,
    val requested: Int,
    val confirmed: Int,
    val cancelled: Int,
    val expired: Int,
    val consumed: Int,
    val rejected: Int
) {
    companion object {
        fun from(
            events: List<VasuConfirmationAuditEvent>
        ): VasuConfirmationAuditSummary {

            fun count(
                type: VasuConfirmationAuditEvent.EventType
            ): Int = events.count { it.eventType == type }

            return VasuConfirmationAuditSummary(
                total = events.size,
                requested = count(VasuConfirmationAuditEvent.EventType.REQUESTED),
                confirmed = count(VasuConfirmationAuditEvent.EventType.CONFIRMED),
                cancelled = count(VasuConfirmationAuditEvent.EventType.CANCELLED),
                expired = count(VasuConfirmationAuditEvent.EventType.EXPIRED),
                consumed = count(VasuConfirmationAuditEvent.EventType.CONSUMED),
                rejected = count(VasuConfirmationAuditEvent.EventType.REJECTED)
            )
        }
    }
}
