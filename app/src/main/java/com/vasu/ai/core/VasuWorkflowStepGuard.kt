package com.vasu.ai.core

/** Decides whether a failed workflow step can be recovered automatically. */
object VasuWorkflowStepGuard {
    fun isRecoverable(action: VasuAction, reason: String): Boolean {
        when (action) {
            is VasuAction.CallContact,
            is VasuAction.SendSms -> return false
            else -> Unit
        }

        val normalized = reason.lowercase()
        if (normalized.contains("permission") ||
            normalized.contains("cancel") ||
            normalized.contains("authentication") ||
            normalized.contains("password") ||
            normalized.contains("locked")) {
            return false
        }
        return true
    }
}
