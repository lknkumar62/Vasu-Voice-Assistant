package com.vasu.ai.core

class VasuConfirmationAuditLog(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {

    private val events = ArrayDeque<VasuConfirmationAuditEvent>()

    @Synchronized
    fun record(event: VasuConfirmationAuditEvent) {
        if (maxEntries <= 0) return

        while (events.size >= maxEntries) {
            events.removeFirst()
        }

        events.addLast(
            event.copy(
                requestId = VasuConfirmationAuditSanitizer
                    .sanitizeRequestId(event.requestId),
                reason = VasuConfirmationAuditSanitizer
                    .sanitizeReason(event.reason)
            )
        )
    }

    @Synchronized
    fun snapshot(): List<VasuConfirmationAuditEvent> =
        events.toList()

    @Synchronized
    fun snapshotData(): VasuConfirmationAuditSnapshot =
        VasuConfirmationAuditSnapshot.from(events.toList())

    @Synchronized
    fun clear() {
        events.clear()
    }

    @Synchronized
    fun size(): Int = events.size

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
