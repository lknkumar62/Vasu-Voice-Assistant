package com.vasu.ai.core

import android.os.SystemClock

/**
 * Centralized bounded timeout controller.
 *
 * This class does not execute actions.
 * It only provides deadline and timeout decisions.
 */
class VasuTimeoutController {

    companion object {
        const val DEFAULT_ACTION_TIMEOUT_MS = 5000L
        const val OPEN_APP_TIMEOUT_MS = 8000L
        const val UI_WAIT_TIMEOUT_MS = 3000L
        const val WORKFLOW_TIMEOUT_MS = 30000L
    }

    data class Deadline(
        val startedAt: Long,
        val timeoutMs: Long
    ) {
        fun expired(): Boolean =
            SystemClock.uptimeMillis() - startedAt >= timeoutMs

        fun remainingMs(): Long {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            return (timeoutMs - elapsed).coerceAtLeast(0L)
        }
    }

    fun start(timeoutMs: Long = DEFAULT_ACTION_TIMEOUT_MS): Deadline =
        Deadline(
            startedAt = SystemClock.uptimeMillis(),
            timeoutMs = timeoutMs.coerceAtLeast(1L)
        )
}
