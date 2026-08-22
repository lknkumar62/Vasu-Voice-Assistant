package com.vasu.ai.core

class VasuClarificationResumeResolver {

    fun resolve(
        answer: VasuClarificationAnswer
    ): VasuClarificationResumeResult {
        if (!answer.matched) {
            return VasuClarificationResumeResult(
                resolved = false,
                requiresFreshUi = true,
                referenceType = VasuReferenceType.UNKNOWN,
                reason = "UNRESOLVED_CLARIFICATION"
            )
        }

        if (!answer.requiresFreshUiEvidence) {
            return VasuClarificationResumeResult(
                resolved = false,
                requiresFreshUi = true,
                referenceType = answer.referenceType,
                reason = "FRESH_UI_REQUIRED"
            )
        }

        return VasuClarificationResumeResult(
            resolved = true,
            requiresFreshUi = true,
            referenceType = answer.referenceType,
            reason = "REFERENCE_RESOLVED"
        )
    }
}
