package com.vasu.ai.core

class VasuFollowUpResolver(
    private val contextStore: VasuConversationContextStore,
    private val referenceResolver: VasuReferenceResolver = VasuReferenceResolver(contextStore),
    private val clarificationManager: VasuClarificationManager = VasuClarificationManager(),
    private val clarificationResumeResolver: VasuClarificationResumeResolver = VasuClarificationResumeResolver(),
    private val freshReferenceResolver: VasuFreshReferenceResolver = VasuFreshReferenceResolver()
) {
    data class Resolution(
        val isFollowUp: Boolean,
        val resolvedCommand: String,
        val context: VasuConversationContext,
        val reference: VasuConversationReference,
        val clarificationAnswer: VasuClarificationAnswer? = null,
        val clarificationHandled: Boolean = false
    )

    fun resolve(input: String): Resolution {
        val context = contextStore.get()
        val pending = clarificationManager.getPendingRequest()

        if (pending != null) {
            if (clarificationManager.shouldTreatAsClarificationAnswer(input)) {
                val answer = clarificationManager.resolveAnswer(input)
                if (answer != null) {
                    val resume = clarificationResumeResolver.resolve(answer)
                    val freshRequired =
                        resume.requiresFreshUi || freshReferenceResolver.requiresFreshResolution(answer.referenceType)

                    println(
                        "VASU_CLARIFICATION_RESUME " +
                            "reference=${answer.referenceType} " +
                            "resolved=${resume.resolved} " +
                            "freshUi=$freshRequired " +
                            "reason=${resume.reason}"
                    )

                    val reference = VasuConversationReference(
                        type = if (resume.resolved) answer.referenceType else VasuReferenceType.UNKNOWN,
                        originalText = input,
                        confidence = answer.confidence,
                        requiresFreshUiEvidence = freshRequired
                    )

                    println(
                        "VASU_CLARIFICATION_ANSWER " +
                            "type=${answer.referenceType} " +
                            "confidence=${answer.confidence}"
                    )

                    return Resolution(
                        isFollowUp = true,
                        resolvedCommand = if (resume.resolved) pending.originalCommand else input,
                        context = context,
                        reference = reference,
                        clarificationAnswer = answer,
                        clarificationHandled = true
                    )
                }
            } else {
                clarificationManager.cancel()
                clarificationManager.clear()
                println("VASU_CLARIFICATION_CANCELLED_FOR_NEW_COMMAND")
            }
        }

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
