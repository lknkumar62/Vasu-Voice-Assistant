package com.vasu.ai.core

object VasuConfirmationAuditSanitizer {

    private const val MAX_ACTION_LENGTH = 80
    private const val MAX_REQUEST_ID_LENGTH = 100
    private const val MAX_REASON_LENGTH = 200

    fun sanitizeReason(reason: String): String =
        reason
            .trim()
            .take(MAX_REASON_LENGTH)

    fun sanitizeRequestId(requestId: String?): String? =
        requestId
            ?.trim()
            ?.take(MAX_REQUEST_ID_LENGTH)
            ?.takeIf { it.isNotBlank() }

    fun sanitizeActionName(actionName: String?): String? =
        actionName
            ?.trim()
            ?.take(MAX_ACTION_LENGTH)
            ?.takeIf { it.isNotBlank() }
}
