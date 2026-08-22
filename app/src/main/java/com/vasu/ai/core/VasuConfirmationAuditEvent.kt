package com.vasu.ai.core

data class VasuConfirmationAuditEvent(
    val eventType: EventType,
    val actionType: VasuSecurityActionType?,
    val requestId: String?,
    val timestamp: Long,
    val reason: String = ""
) {
    enum class EventType {
        REQUESTED,
        CONFIRMED,
        CANCELLED,
        EXPIRED,
        CONSUMED,
        REJECTED
    }
}
