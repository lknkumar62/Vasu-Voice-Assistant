package com.vasu.ai.memory

class VasuMemoryCleanupPolicy(
    private val maxEntries: Int = 100
) {

    fun shouldTrim(entryCount: Int): Boolean {
        return entryCount > maxEntries
    }

    fun allowedEntryCount(): Int {
        return maxEntries.coerceAtLeast(1)
    }
}
