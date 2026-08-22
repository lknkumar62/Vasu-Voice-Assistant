package com.vasu.ai.memory

class VasuMemoryKeyNormalizer {

    fun normalize(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .take(MAX_KEY_LENGTH)
    }

    companion object {
        private const val MAX_KEY_LENGTH = 120
    }
}
