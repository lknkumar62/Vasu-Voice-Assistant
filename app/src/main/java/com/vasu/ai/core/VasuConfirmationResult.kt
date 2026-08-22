package com.vasu.ai.core

sealed class VasuConfirmationResult {
    data object Allowed : VasuConfirmationResult()

    data class ConfirmationRequired(
        val request: VasuConfirmationRequest
    ) : VasuConfirmationResult()

    data object Rejected : VasuConfirmationResult()
}
