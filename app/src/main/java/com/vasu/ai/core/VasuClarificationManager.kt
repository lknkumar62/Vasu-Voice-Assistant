package com.vasu.ai.core

class VasuClarificationManager(
    private val store: VasuClarificationStore = VasuClarificationStore(),
    private val resolver: VasuClarificationResolver = VasuClarificationResolver()
) {
    fun requestClarification(
        originalCommand: String,
        question: String,
        referenceType: VasuReferenceType
    ): VasuClarificationRequest = store.create(
        originalCommand = originalCommand,
        question = question,
        referenceType = referenceType
    )

    fun hasPendingRequest(): Boolean = store.getActive() != null

    fun getPendingRequest(): VasuClarificationRequest? = store.getActive()

    fun resolveAnswer(answer: String): VasuClarificationAnswer? {
        val request = store.getActive() ?: return null
        val result = resolver.resolve(request, answer)
        if (result.normalizedAnswer == "cancel") {
            store.cancel()
            return result
        }
        if (!result.matched) {
            println("VASU_CLARIFICATION_UNRESOLVED")
            return result
        }
        store.resolve()
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

    fun cancel() = store.cancel()
    fun clear() = store.clear()
}
