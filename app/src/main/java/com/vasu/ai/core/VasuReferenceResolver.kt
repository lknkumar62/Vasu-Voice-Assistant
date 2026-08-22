package com.vasu.ai.core

class VasuReferenceResolver(
    private val contextStore: VasuConversationContextStore,
    private val detector: VasuFollowUpIntentDetector = VasuFollowUpIntentDetector()
) {
    data class Result(
        val reference: VasuConversationReference,
        val context: VasuConversationReferenceContext,
        val canResolveWithoutUi: Boolean
    )

    fun resolve(input: String): Result {
        val context = contextStore.get()
        val reference = detector.detect(input)
        val canResolveWithoutUi = when (reference.type) {
            VasuReferenceType.CONFIRMATION,
            VasuReferenceType.CONTINUE,
            VasuReferenceType.ACTIVE_APP -> true
            else -> false
        }

        val resultContext = VasuConversationReferenceContext(
            activeAppName = context.activeAppName,
            activeAppPackage = context.activeAppPackage,
            lastUserCommand = context.lastUserCommand,
            lastSuccessfulAction = context.lastSuccessfulAction,
            referenceType = reference.type,
            referenceConfidence = reference.confidence,
            freshUiEvidenceRequired = reference.requiresFreshUiEvidence
        )

        println(
            "VASU_REFERENCE_RESOLUTION " +
                "type=${reference.type} " +
                "confidence=${reference.confidence} " +
                "freshUi=${reference.requiresFreshUiEvidence}"
        )

        return Result(reference, resultContext, canResolveWithoutUi)
    }
}
