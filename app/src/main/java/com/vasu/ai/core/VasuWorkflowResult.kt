package com.vasu.ai.core

sealed class VasuWorkflowResult {
    data object Success : VasuWorkflowResult()
    data class StepFailed(
        val stepIndex: Int,
        val reason: String,
        val recoverable: Boolean
    ) : VasuWorkflowResult()
    data class Failed(val reason: String) : VasuWorkflowResult()
    data object Cancelled : VasuWorkflowResult()
}
