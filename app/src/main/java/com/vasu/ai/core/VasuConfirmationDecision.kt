package com.vasu.ai.core

sealed class VasuConfirmationDecision {
    data object Allowed : VasuConfirmationDecision()

    data class RequiresConfirmation(
        val request: VasuConfirmationRequest
    ) : VasuConfirmationDecision()

    data object Rejected : VasuConfirmationDecision()
}
