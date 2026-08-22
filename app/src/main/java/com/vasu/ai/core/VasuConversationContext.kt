package com.vasu.ai.core

data class VasuConversationContext(
    val sessionId: String,
    val state: VasuConversationState,
    val lastUserCommand: String? = null,
    val lastAssistantResponse: String? = null,
    val activeAppName: String? = null,
    val activeAppPackage: String? = null,
    val lastSuccessfulAction: String? = null,
    val lastWorkflowSuccessful: Boolean = false,
    val lastUpdatedMs: Long,
    val turns: List<VasuConversationTurn> = emptyList()
)
