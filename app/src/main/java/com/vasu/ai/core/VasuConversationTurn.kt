package com.vasu.ai.core

data class VasuConversationTurn(
    val userText: String,
    val assistantText: String? = null,
    val timestampMs: Long,
    val successful: Boolean = false
)
