package com.vasu.ai.core

data class VasuClarificationSession(
    val requestId: String,
    val originalCommand: String,
    val referenceType: VasuReferenceType,
    val lifecycle: VasuClarificationLifecycle,
    val createdAtMs: Long,
    val lastUpdatedAtMs: Long,
    val expiresAtMs: Long,
    val attemptCount: Int = 0
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs >= expiresAtMs
}
