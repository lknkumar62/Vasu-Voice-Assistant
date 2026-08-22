package com.vasu.ai.core

class VasuFollowUpResolver(
    private val contextStore: VasuConversationContextStore,
    private val referenceResolver: VasuReferenceResolver = VasuReferenceResolver(contextStore)
) {
    data class Resolution(
        val isFollowUp: Boolean,
        val resolvedCommand: String,
        val context: VasuConversationContext,
        val reference: VasuConversationReference
    )

    fun resolve(input: String): Resolution {
        val context = contextStore.get()
        val referenceResult = referenceResolver.resolve(input)
        val reference = referenceResult.reference

        if (reference.type == VasuReferenceType.NONE) {
            return Resolution(false, input, context, reference)
        }

        if (reference.confidence < 0.70f) {
            return Resolution(true, input, context, reference.copy(type = VasuReferenceType.UNKNOWN))
        }

        val resolved = resolveReference(input, context, reference)
        println(
            "VASU_FOLLOW_UP_RESOLUTION " +
                "isFollowUp=true resolvedCommand=$resolved reference=${reference.type}"
        )
        return Resolution(true, resolved, context, reference)
    }

    private fun resolveReference(
        input: String,
        context: VasuConversationContext,
        reference: VasuConversationReference
    ): String {
        val normalized = input.trim().lowercase()
        val app = context.activeAppName

        return when (reference.type) {
            VasuReferenceType.CONFIRMATION -> input
            VasuReferenceType.CONTINUE -> when {
                normalized.startsWith("haan ") -> removeFollowUpPrefix(input, "haan")
                normalized.startsWith("ha ") -> removeFollowUpPrefix(input, "ha")
                normalized.startsWith("phir ") -> removeFollowUpPrefix(input, "phir")
                normalized.startsWith("fir ") -> removeFollowUpPrefix(input, "fir")
                normalized == "continue" -> if (!app.isNullOrBlank()) "continue with $app" else input
                else -> input
            }
            else -> input
        }
    }

    private fun removeFollowUpPrefix(input: String, prefix: String): String =
        input.trim().substringAfter(prefix, "").trim()
}
