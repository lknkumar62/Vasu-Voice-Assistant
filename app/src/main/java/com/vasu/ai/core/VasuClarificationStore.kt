package com.vasu.ai.core

class VasuClarificationStore {
    companion object {
        private const val DEFAULT_TIMEOUT_MS = 60_000L
    }

    private var request: VasuClarificationRequest? = null

    @Synchronized
    fun create(
        originalCommand: String,
        question: String,
        referenceType: VasuReferenceType,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): VasuClarificationRequest {
        val now = System.currentTimeMillis()
        val newRequest = VasuClarificationRequest(
            id = "clarification-$now",
            originalCommand = originalCommand,
            question = question,
            referenceType = referenceType,
            createdAtMs = now,
            expiresAtMs = now + timeoutMs
        )
        request = newRequest
        println("VASU_CLARIFICATION_CREATED type=$referenceType")
        return newRequest
    }

    @Synchronized
    fun getActive(): VasuClarificationRequest? {
        val current = request ?: return null
        if (current.isExpired()) {
            request = current.copy(state = VasuClarificationState.EXPIRED)
            println("VASU_CLARIFICATION_EXPIRED")
            return null
        }
        return current
    }

    @Synchronized
    fun resolve(): VasuClarificationRequest? {
        val current = getActive() ?: return null
        val resolved = current.copy(state = VasuClarificationState.RESOLVED)
        request = resolved
        println("VASU_CLARIFICATION_RESOLVED")
        return resolved
    }

    @Synchronized
    fun cancel() {
        val current = request ?: return
        request = current.copy(state = VasuClarificationState.CANCELLED)
        println("VASU_CLARIFICATION_CANCELLED")
    }

    @Synchronized
    fun clear() {
        request = null
    }
}
