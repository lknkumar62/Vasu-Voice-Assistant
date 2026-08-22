package com.vasu.ai.core

data class VasuClarificationContext(
    val requestId: String,
    val originalCommand: String,
    val referenceType: VasuReferenceType,
    val question: String,
    val createdAtMs: Long,
    val expiresAtMs: Long
)
