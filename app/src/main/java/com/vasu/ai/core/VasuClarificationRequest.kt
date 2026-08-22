package com.vasu.ai.core

data class VasuClarificationRequest(
    val id: String,
    val originalCommand: String,
    val question: String,
    val referenceType: VasuReferenceType,
    val createdAtMs: Long,
    val expiresAtMs: Long,
    val state: VasuClarificationState = VasuClarificationState.WAITING_FOR_USER
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs >= expiresAtMs
}
