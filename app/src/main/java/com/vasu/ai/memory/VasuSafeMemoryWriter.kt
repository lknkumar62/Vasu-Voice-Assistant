package com.vasu.ai.memory

class VasuSafeMemoryWriter(
    private val store: VasuMemoryStore,
    private val safetyPolicy: VasuMemorySafetyPolicy =
        VasuMemorySafetyPolicy()
) {

    fun remember(key: String, value: String): Boolean {
        if (!safetyPolicy.isAllowedKey(key)) {
            return false
        }

        if (value.trim().isBlank()) {
            return false
        }

        store.remember(key, value)
        return true
    }

    fun rememberPreference(key: String, value: String): Boolean {
        if (!safetyPolicy.isAllowedKey(key)) {
            return false
        }

        if (value.trim().isBlank()) {
            return false
        }

        store.rememberPreference(key, value)
        return true
    }
}
