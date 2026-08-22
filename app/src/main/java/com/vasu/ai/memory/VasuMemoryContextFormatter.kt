package com.vasu.ai.memory

class VasuMemoryContextFormatter {

    fun format(
        snapshot: VasuMemorySnapshot,
        maxEntries: Int = 10
    ): String {
        val entries = snapshot.entries
            .take(maxEntries.coerceAtLeast(0))

        if (entries.isEmpty()) return ""

        return buildString {
            append("Known user memories:\n")

            entries.forEach { entry ->
                append("- ")
                append(entry.key)
                append(": ")
                append(entry.value)
                append('\n')
            }
        }.trim()
    }
}
