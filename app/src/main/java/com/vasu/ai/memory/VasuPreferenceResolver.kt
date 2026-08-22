package com.vasu.ai.memory

class VasuPreferenceResolver(
    private val store: VasuMemoryStore
) {

    fun remember(key: String, value: String): Boolean {
        if (key.isBlank() || value.isBlank()) {
            return false
        }

        store.rememberPreference(key, value)
        return true
    }

    fun recall(key: String): String? {
        return store.recallPreference(key)
    }

    fun forget(key: String): Boolean {
        if (key.isBlank()) {
            return false
        }

        store.forgetPreference(key)
        return true
    }
}
