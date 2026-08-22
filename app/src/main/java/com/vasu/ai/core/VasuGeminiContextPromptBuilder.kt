package com.vasu.ai.core

class VasuGeminiContextPromptBuilder {
    fun build(
        command: String,
        context: VasuGeminiConversationContext,
        isFollowUp: Boolean
    ): String {
        if (!isFollowUp) return command

        val lines = mutableListOf<String>()
        lines += "Current user command: $command"
        lines += "This may be a follow-up command."
        context.activeAppName?.takeIf { it.isNotBlank() }?.let {
            lines += "Active app from previous successful action: $it"
        }
        context.activeAppPackage?.takeIf { it.isNotBlank() }?.let {
            lines += "Active app package: $it"
        }
        context.lastUserCommand?.takeIf { it.isNotBlank() }?.let {
            lines += "Previous user command: $it"
        }
        context.lastSuccessfulAction?.takeIf { it.isNotBlank() }?.let {
            lines += "Previous successful action: $it"
        }
        lines += "Resolve references only when supported by the supplied context."
        lines += "Do not invent an app, target, element, or previous result."
        return lines.joinToString("\n")
    }
}
