package com.vasu.ai.core

class VasuClarificationSessionManager {
    companion object {
        private const val SESSION_TIMEOUT_MS = 60_000L
        private const val MAX_CLARIFICATION_ATTEMPTS = 3
    }

    private var session: VasuClarificationSession? = null

    @Synchronized
    fun start(request: VasuClarificationRequest): VasuClarificationSession {
        val now = System.currentTimeMillis()
        val existing = session
        val preserveAttempt = existing != null &&
            !existing.isExpired(now) &&
            existing.originalCommand == request.originalCommand

        val created = VasuClarificationSession(
            requestId = request.id,
            originalCommand = request.originalCommand,
            referenceType = request.referenceType,
            lifecycle = VasuClarificationLifecycle.ACTIVE,
            createdAtMs = if (preserveAttempt) existing!!.createdAtMs else now,
            lastUpdatedAtMs = now,
            expiresAtMs = now + SESSION_TIMEOUT_MS,
            attemptCount = if (preserveAttempt) existing!!.attemptCount else 0
        )
        session = created
        println("VASU_CLARIFICATION_SESSION_STARTED")
        return created
    }

    @Synchronized
    fun getActive(): VasuClarificationSession? {
        val current = session ?: return null
        if (current.isExpired()) {
            session = current.copy(
                lifecycle = VasuClarificationLifecycle.EXPIRED,
                lastUpdatedAtMs = System.currentTimeMillis()
            )
            println("VASU_CLARIFICATION_SESSION_EXPIRED")
            return null
        }
        return current
    }

    @Synchronized
    fun markWaitingForFreshUi(): VasuClarificationSession? {
        val current = getActive() ?: return null
        return current.copy(
            lifecycle = VasuClarificationLifecycle.WAITING_FOR_FRESH_UI,
            lastUpdatedAtMs = System.currentTimeMillis()
        ).also { session = it }
    }

    @Synchronized
    fun incrementAttempt(): VasuClarificationSession? {
        val current = getActive() ?: return null
        val nextAttempt = current.attemptCount + 1
        if (nextAttempt > MAX_CLARIFICATION_ATTEMPTS) {
            session = current.copy(
                lifecycle = VasuClarificationLifecycle.CANCELLED,
                lastUpdatedAtMs = System.currentTimeMillis(),
                attemptCount = nextAttempt
            )
            println("VASU_CLARIFICATION_MAX_ATTEMPTS")
            return null
        }
        return current.copy(
            lifecycle = VasuClarificationLifecycle.ANSWERING,
            lastUpdatedAtMs = System.currentTimeMillis(),
            attemptCount = nextAttempt
        ).also { session = it }
    }

    @Synchronized
    fun complete() {
        val current = session ?: return
        session = current.copy(
            lifecycle = VasuClarificationLifecycle.COMPLETED,
            lastUpdatedAtMs = System.currentTimeMillis()
        )
        println("VASU_CLARIFICATION_SESSION_COMPLETED")
    }

    @Synchronized
    fun cancel() {
        val current = session ?: return
        session = current.copy(
            lifecycle = VasuClarificationLifecycle.CANCELLED,
            lastUpdatedAtMs = System.currentTimeMillis()
        )
        println("VASU_CLARIFICATION_SESSION_CANCELLED")
    }

    @Synchronized
    fun clear() {
        session = null
    }
}
