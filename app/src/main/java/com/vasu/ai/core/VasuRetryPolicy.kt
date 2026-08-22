package com.vasu.ai.core

/**
 * Central retry policy.
 *
 * Retry is deliberately conservative.
 */
class VasuRetryPolicy {
    companion object {
        const val DEFAULT_MAX_RETRIES = 2
    }

    fun maxRetriesFor(action: VasuAction): Int = when (action) {
        is VasuAction.CallContact,
        is VasuAction.SendSms -> 0
        else -> DEFAULT_MAX_RETRIES
    }

    fun canRetry(action: VasuAction, attempts: Int): Boolean =
        attempts < maxRetriesFor(action)
}
