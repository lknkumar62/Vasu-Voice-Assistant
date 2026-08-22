package com.vasu.ai.core

class VasuClarificationTrigger(
    private val clarificationManager: VasuClarificationManager
) {
    fun requestForAmbiguousElement(originalCommand: String): VasuClarificationRequest {
        println("VASU_CLARIFICATION_TRIGGER reason=AMBIGUOUS_UI")
        return clarificationManager.requestForAmbiguousCandidates(
            originalCommand = originalCommand.ifBlank { "unknown_command" },
            referenceType = VasuReferenceType.UNKNOWN
        )
    }
}
