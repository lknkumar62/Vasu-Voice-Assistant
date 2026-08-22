package com.vasu.ai.core

class VasuClarificationManager(
    private val store: VasuClarificationStore = VasuClarificationStore(),
    private val resolver: VasuClarificationResolver = VasuClarificationResolver(),
    private val sessionManager: VasuClarificationSessionManager = VasuClarificationSessionManager(),
    private val inputGuard: VasuClarificationInputGuard = VasuClarificationInputGuard()
) {
    fun requestClarification(
        originalCommand: String,
        question: String,
        referenceType: VasuReferenceType
    ): VasuClarificationRequest = store.create(
        originalCommand = originalCommand,
        question = question,
        referenceType = referenceType
    ).also { request ->
        sessionManager.start(request)
    }

    fun hasPendingRequest(): Boolean = store.getActive() != null && sessionManager.getActive() != null

    fun getPendingRequest(): VasuClarificationRequest? =
        if (sessionManager.getActive() != null) store.getActive() else null

    fun shouldTreatAsClarificationAnswer(input: String): Boolean =
        getPendingRequest() != null && inputGuard.looksLikeClarificationAnswer(input)

    fun resolveAnswer(answer: String): VasuClarificationAnswer? {
        val request = getPendingRequest() ?: return null
        if (!inputGuard.looksLikeClarificationAnswer(answer)) return null

        if (answer.trim().equals("cancel", ignoreCase = true) ||
            answer.trim().equals("cancel karo", ignoreCase = true) ||
            answer.trim().equals("rehne do", ignoreCase = true) ||
            answer.trim() == "रहने दो"
        ) {
            sessionManager.cancel()
            store.cancel()
            return resolver.resolve(request, answer)
        }

        if (sessionManager.incrementAttempt() == null) {
            return VasuClarificationAnswer(
                matched = false,
                referenceType = VasuReferenceType.UNKNOWN,
                confidence = 1f,
                normalizedAnswer = "max_attempts",
                requiresFreshUiEvidence = false
            )
        }

        val result = resolver.resolve(request, answer)
        if (!result.matched) {
            println("VASU_CLARIFICATION_UNRESOLVED")
            return result
        }

        sessionManager.markWaitingForFreshUi()
        store.resolve()
        println("VASU_CLARIFICATION_WAITING_FOR_FRESH_UI type=${result.referenceType}")
        return result
    }

    fun clarificationQuestion(referenceType: VasuReferenceType): String = when (referenceType) {
        VasuReferenceType.FIRST_RESULT,
        VasuReferenceType.SECOND_RESULT,
        VasuReferenceType.THIS_ITEM,
        VasuReferenceType.THAT_ITEM,
        VasuReferenceType.PREVIOUS_RESULT -> "Kaunsa wala?"
        else -> "Thoda aur clear batao."
    }

    fun requestForAmbiguousCandidates(
        originalCommand: String,
        referenceType: VasuReferenceType = VasuReferenceType.UNKNOWN
    ): VasuClarificationRequest = requestClarification(
        originalCommand = originalCommand,
        question = clarificationQuestion(referenceType),
        referenceType = referenceType
    )

    fun completeFreshResolution() {
        sessionManager.complete()
        sessionManager.clear()
        store.clear()
    }

    fun cancel() {
        sessionManager.cancel()
        store.cancel()
    }

    fun clear() {
        sessionManager.clear()
        store.clear()
    }
}
