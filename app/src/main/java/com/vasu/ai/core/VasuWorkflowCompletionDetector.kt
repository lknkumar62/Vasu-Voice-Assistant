package com.vasu.ai.core

/**
 * Determines whether a workflow can be considered complete.
 *
 * Completion must come from workflow state and verified steps.
 * A screen transition alone is never enough.
 */
class VasuWorkflowCompletionDetector {

    data class CompletionResult(
        val complete: Boolean,
        val reason: String
    )

    fun evaluate(state: VasuWorkflowState): CompletionResult {
        if (!state.workflowStarted) {
            return CompletionResult(false, "workflow_not_started")
        }
        if (state.workflowFailed) {
            return CompletionResult(false, state.failureReason ?: "workflow_failed")
        }
        if (state.currentStepIndex < 0) {
            return CompletionResult(false, "no_steps")
        }
        val steps = state.snapshot()
        if (steps.isEmpty()) {
            return CompletionResult(false, "no_step_records")
        }
        if (!steps.all { it.verified }) {
            return CompletionResult(false, "unverified_step")
        }
        return CompletionResult(true, "all_steps_verified")
    }
}
