package com.vasu.ai.core

data class VasuConfirmationRequest(
    val id: String,
    val actionType: VasuSecurityActionType,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long
)
