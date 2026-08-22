package com.vasu.ai.core

data class VasuConfirmationAuditSnapshot(
    val events: List<VasuConfirmationAuditEvent>,
    val size: Int
) {
    companion object {
        fun from(
            events: List<VasuConfirmationAuditEvent>
        ): VasuConfirmationAuditSnapshot =
            VasuConfirmationAuditSnapshot(
                events = events.toList(),
                size = events.size
            )
    }
}
