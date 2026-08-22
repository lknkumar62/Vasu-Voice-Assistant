package com.vasu.ai.memory

object VasuMemoryBoundary {

    const val MAX_KEY_LENGTH = 120
    const val MAX_VALUE_LENGTH = 1000
    const val MAX_CONTEXT_ENTRIES = 20

    fun safeKey(key: String): String {
        return key
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .take(MAX_KEY_LENGTH)
    }

    fun safeValue(value: String): String {
        return value
            .trim()
            .take(MAX_VALUE_LENGTH)
    }
}
