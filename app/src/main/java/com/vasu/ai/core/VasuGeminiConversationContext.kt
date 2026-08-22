package com.vasu.ai.core

data class VasuGeminiConversationContext(
    val activeAppName: String?,
    val activeAppPackage: String?,
    val lastUserCommand: String?,
    val lastAssistantResponse: String?,
    val lastSuccessfulAction: String?
)
