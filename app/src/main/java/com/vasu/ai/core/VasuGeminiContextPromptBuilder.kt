package com.vasu.ai.core

class VasuGeminiContextPromptBuilder {
    fun build(
        command: String,
        context: VasuGeminiConversationContext,
        isFollowUp: Boolean,
        reference: VasuConversationReference? = null
    ): String {
        if (!isFollowUp && reference?.type == VasuReferenceType.NONE) return command

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
        reference?.let {
            lines += "Conversation reference: ${it.type}"
            lines += "Reference confidence: ${it.confidence}"
            lines += "Fresh UI evidence required: ${it.requiresFreshUiEvidence}"
        }
        lines += "Resolve references only when supported by the supplied context."
        lines += "Never invent the meaning of an ambiguous reference."
        lines += "Do not invent an app, target, element, or previous result."
        lines += "If this command resolves a pending clarification, use the supplied reference type."
        lines += "Clarification-derived references require fresh current-screen UI evidence."
        lines += "Never convert an ambiguous reference directly into a click without verified UI evidence."
        if (reference?.requiresFreshUiEvidence == true) {
            lines += "Inspect the current screen and require reliable fresh accessibility evidence before selecting a referenced item."
            lines += "If no reliable candidate exists or candidates are equally plausible, do not execute the action."
        }
        return lines.joinToString("\n")
    }
}
