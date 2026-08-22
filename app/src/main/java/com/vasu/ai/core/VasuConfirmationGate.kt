package com.vasu.ai.core

class VasuConfirmationGate(
    private val classifier: VasuActionSecurityClassifier =
        VasuActionSecurityClassifier(),
    private val confirmationManager: VasuConfirmationManager =
        VasuConfirmationManager()
) {

    @Synchronized
    fun evaluate(
        action: VasuAction,
        description: String,
        now: Long = System.currentTimeMillis()
    ): VasuConfirmationDecision {
        val type = classifier.classify(action)

        if (!confirmationPolicy(type)) {
            return VasuConfirmationDecision.Allowed
        }

        val request = confirmationManager.requestConfirmation(
            actionType = type,
            description = description,
            now = now
        ) ?: return VasuConfirmationDecision.Rejected

        return VasuConfirmationDecision.RequiresConfirmation(request)
    }

    @Synchronized
    fun confirm(
        requestId: String,
        now: Long = System.currentTimeMillis()
    ): Boolean = confirmationManager.confirm(requestId, now)

    @Synchronized
    fun cancel(requestId: String): Boolean =
        confirmationManager.cancel(requestId)

    fun pendingRequest(): VasuConfirmationRequest? =
        confirmationManager.getPendingRequest()

    fun state(): VasuConfirmationState =
        confirmationManager.getState()

    private fun confirmationPolicy(
        type: VasuSecurityActionType
    ): Boolean = VasuConfirmationPolicy().requiresConfirmation(type)
}
