package com.vasu.ai.core

object VasuConfirmationAuditFilter {

    fun byEventType(
        events: List<VasuConfirmationAuditEvent>,
        eventType: VasuConfirmationAuditEvent.EventType
    ): List<VasuConfirmationAuditEvent> =
        events.filter { it.eventType == eventType }

    fun byActionType(
        events: List<VasuConfirmationAuditEvent>,
        actionType: VasuSecurityActionType
    ): List<VasuConfirmationAuditEvent> =
        events.filter { it.actionType == actionType }

    fun rejected(
        events: List<VasuConfirmationAuditEvent>
    ): List<VasuConfirmationAuditEvent> =
        events.filter {
            it.eventType == VasuConfirmationAuditEvent.EventType.REJECTED
        }
}
