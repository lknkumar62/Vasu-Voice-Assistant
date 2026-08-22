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

        events.addLast(event)
    }

    @Synchronized
    fun snapshot(): List<VasuConfirmationAuditEvent> =
        events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }

    companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
