package com.vasu.ai.core

import android.os.SystemClock

/**
 * Workflow-level reliability utilities.
 *
 * This class does not execute actions. It only controls bounded waits,
 * retry budgets and duplicate-action protection.
 */
class VasuWorkflowReliability {

    companion object {
        const val DEFAULT_ACTION_TIMEOUT_MS = 5000L
        const val SCREEN_STABLE_TIMEOUT_MS = 2500L
        const val SCREEN_STABLE_POLL_MS = 100L
        const val MAX_SAFE_RETRIES = 2
        const val DUPLICATE_ACTION_WINDOW_MS = 1200L
    }

    data class RetryState(
        val attempt: Int = 0,
        val maxAttempts: Int = MAX_SAFE_RETRIES
    ) {
        fun canRetry(): Boolean = attempt < maxAttempts
        fun next(): RetryState = copy(attempt = attempt + 1)
    }

    data class ActionSignature(
        val actionType: String,
        val target: String?,
        val text: String?,
        val packageName: String?
    )

    private var lastSignature: ActionSignature? = null
    private var lastExecutionTime: Long = 0L

    fun isDuplicate(signature: ActionSignature): Boolean {
        val now = SystemClock.uptimeMillis()
        val duplicate = lastSignature == signature &&
            now - lastExecutionTime < DUPLICATE_ACTION_WINDOW_MS

        if (duplicate) {
            println(
                "VASU_WORKFLOW_DUPLICATE " +
                    "action=${signature.actionType} target=${signature.target}"
            )
        }

        return duplicate
    }

    fun recordExecution(signature: ActionSignature) {
        lastSignature = signature
        lastExecutionTime = SystemClock.uptimeMillis()
    }

    fun resetDuplicateGuard() {
        lastSignature = null
        lastExecutionTime = 0L
    }

    fun boundedSleep(delayMs: Long) {
        if (delayMs <= 0L) return
        val safeDelay = delayMs.coerceAtMost(DEFAULT_ACTION_TIMEOUT_MS)
        try {
            Thread.sleep(safeDelay)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            println("VASU_WORKFLOW_WAIT interrupted=true")
        }
    }

    fun shouldRetry(retryState: RetryState, actionType: String): Boolean {
        if (!retryState.canRetry()) {
            println(
                "VASU_WORKFLOW_RETRY " +
                    "action=$actionType allowed=false reason=max_attempts"
            )
            return false
        }

        println(
            "VASU_WORKFLOW_RETRY " +
                "action=$actionType attempt=${retryState.attempt + 1}"
        )
        return true
    }
}
