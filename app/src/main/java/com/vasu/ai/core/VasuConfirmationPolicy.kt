package com.vasu.ai.core

class VasuConfirmationPolicy(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {

    fun requiresConfirmation(
        actionType: VasuSecurityActionType
    ): Boolean {
        return when (actionType) {
            VasuSecurityActionType.NORMAL -> false

            VasuSecurityActionType.PHONE_CALL,
            VasuSecurityActionType.SEND_SMS,
            VasuSecurityActionType.CONTACT_CHANGE,
            VasuSecurityActionType.SYSTEM_SETTING,
            VasuSecurityActionType.EXTERNAL_ACTION -> true
        }
    }

    fun expirationTime(now: Long = System.currentTimeMillis()): Long {
        return now + timeoutMs.coerceAtLeast(1L)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
    }
}
