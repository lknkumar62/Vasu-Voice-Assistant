package com.vasu.ai.core

class VasuGeminiConversationContextMapper {
    fun map(context: VasuConversationContext): VasuGeminiConversationContext =
        VasuGeminiConversationContext(
            activeAppName = context.activeAppName,
            activeAppPackage = context.activeAppPackage,
            lastUserCommand = context.lastUserCommand,
            lastAssistantResponse = context.lastAssistantResponse,
            lastSuccessfulAction = context.lastSuccessfulAction
        )
}
