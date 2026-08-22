package com.vasu.ai.core

class VasuFollowUpResolver(
    private val contextStore: VasuConversationContextStore
) {
    data class Resolution(
        val isFollowUp: Boolean,
        val resolvedCommand: String,
        val context: VasuConversationContext
    )

    fun resolve(input: String): Resolution {
        val context = contextStore.get()
        val isFollowUp = contextStore.isFollowUpCandidate(input)
        if (!isFollowUp) {
            return Resolution(false, input, context)
        }

        val resolved = resolveReference(input, context)
        println(
            "VASU_FOLLOW_UP_RESOLUTION " +
                "isFollowUp=true resolvedCommand=$resolved"
        )
        return Resolution(true, resolved, context)
    }

    private fun resolveReference(
        input: String,
        context: VasuConversationContext
    ): String {
        val normalized = input.trim().lowercase()
        val app = context.activeAppName

        return when {
            normalized in setOf("haan", "ha", "yes", "okay", "ok") -> {
                if (!app.isNullOrBlank()) "continue with $app" else input
            }
            normalized.startsWith("haan ") -> removeFollowUpPrefix(input, "haan")
            normalized.startsWith("ha ") -> removeFollowUpPrefix(input, "ha")
            normalized.startsWith("phir ") -> removeFollowUpPrefix(input, "phir")
            normalized.startsWith("fir ") -> removeFollowUpPrefix(input, "fir")
            else -> input
        }
    }

    private fun removeFollowUpPrefix(input: String, prefix: String): String =
        input.trim().substringAfter(prefix, "").trim()
}
