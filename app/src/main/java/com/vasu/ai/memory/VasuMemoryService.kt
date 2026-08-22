package com.vasu.ai.memory

class VasuMemoryService(
    private val writer: VasuSafeMemoryWriter
) {
    fun remember(key: String, value: String): VasuMemoryWriteResult {
        return if (writer.remember(key, value)) {
            VasuMemoryWriteResult.Saved
        } else {
            VasuMemoryWriteResult.Rejected
        }
    }

    fun rememberPreference(
        key: String,
        value: String
    ): VasuMemoryWriteResult {
        return if (writer.rememberPreference(key, value)) {
            VasuMemoryWriteResult.Saved
        } else {
            VasuMemoryWriteResult.Rejected
        }
    }
}
