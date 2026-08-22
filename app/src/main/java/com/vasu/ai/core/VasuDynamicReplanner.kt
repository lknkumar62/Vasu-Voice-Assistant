package com.vasu.ai.core

import com.vasu.ai.accessibility.VasuAccessibilityService

/** Builds non-executing recovery context for the current workflow step. */
class VasuDynamicReplanner {
    data class RecoveryContext(
        val originalCommand: String,
        val failedAction: VasuAction?,
        val failureReason: String,
        val foregroundPackage: String?,
        val visibleScreen: String,
        val navigationHints: List<String>,
        val workflowStepIndex: Int,
        val lastActionVerified: Boolean
    ) {
        fun asPromptContext(): String = buildString {
            append("DYNAMIC_RECOVERY=true\n")
            append("original_command=").append(originalCommand).append('\n')
            append("workflow_step=").append(workflowStepIndex).append('\n')
            append("failed_action=").append(failedAction?.toString() ?: "unknown").append('\n')
            append("last_action_verified=").append(lastActionVerified).append('\n')
            append("failure_reason=").append(failureReason).append('\n')
            append("foreground_package=").append(foregroundPackage ?: "unknown").append('\n')
            append("CURRENT_VISIBLE_SCREEN=\n").append(visibleScreen).append('\n')
            append("KNOWN_NAVIGATION_HINTS=")
                .append(navigationHints.joinToString(", ").ifBlank { "none" })
                .append('\n')
            append(
                """
                RECOVERY_RULES:
                - Recover only the current failed workflow step; do not restart already verified work.
                - Do not blindly repeat the failed action.
                - Inspect the current screen and choose a different safe next action when possible.
                - If a prerequisite screen must be opened first, perform that prerequisite action.
                - If the requested target is genuinely unavailable, do not invent a target.
                - Navigation hints are hints only; never assume a hinted element exists without fresh accessibility evidence.
                - A screen transition alone is not proof of action success.
                - Never bypass Android permissions, locks, authentication, or security controls.
                - Never repeat calls, SMS, payments, or other sensitive side effects automatically.
                - Use exactly one next action.
                - Set done=false unless the original user task is actually complete.
                """.trimIndent()
            )
        }
    }

    fun capture(
        originalCommand: String,
        execution: VasuExecutionEngine.ExecutionResult?
    ): RecoveryContext {
        val service = VasuAccessibilityService.instance
        val failedStep = execution?.steps?.lastOrNull { !it.success }
        val foregroundPackage = service?.foregroundPackage()
        val failureReason = failedStep?.let {
            buildString {
                append("action_execution_failed")
                append("; attempts=").append(it.attempts)
                append("; verification=").append(it.verification)
                append("; reason=").append(it.verificationReason)
            }
        } ?: "execution_failed_without_step_details"

        return RecoveryContext(
            originalCommand = originalCommand,
            failedAction = failedStep?.action,
            failureReason = failureReason,
            foregroundPackage = foregroundPackage,
            visibleScreen = service?.describeScreen(160) ?: "accessibility_screen_unavailable",
            navigationHints = VasuNavigationHints.hintsFor(foregroundPackage),
            workflowStepIndex = (execution?.steps?.size ?: 1) - 1,
            lastActionVerified = execution?.steps?.lastOrNull()?.success == true
        )
    }
}
