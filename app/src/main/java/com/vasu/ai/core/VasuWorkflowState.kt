package com.vasu.ai.core

/** Session-scoped state for one autonomous workflow. */
class VasuWorkflowState {
    data class StepRecord(
        val index: Int,
        val actionType: String,
        val startedAt: Long,
        var completedAt: Long? = null,
        var verified: Boolean = false,
        var attempts: Int = 0,
        var failureReason: String? = null
    )

    private val steps = mutableListOf<StepRecord>()

    var currentStepIndex: Int = -1
        private set
    var workflowStarted: Boolean = false
        private set
    var workflowCompleted: Boolean = false
        private set
    var workflowFailed: Boolean = false
        private set
    var failureReason: String? = null
        private set

    fun start() {
        workflowStarted = true
        workflowCompleted = false
        workflowFailed = false
        failureReason = null
        currentStepIndex = -1
        steps.clear()
        println("VASU_WORKFLOW_STATE started=true")
    }

    fun beginStep(index: Int, actionType: String, now: Long): StepRecord {
        currentStepIndex = index
        val record = StepRecord(index, actionType, now)
        steps += record
        println("VASU_WORKFLOW_STEP begin=$index action=$actionType")
        return record
    }

    fun recordAttempt() {
        steps.lastOrNull()?.let {
            it.attempts++
            println("VASU_WORKFLOW_STEP step=${it.index} attempt=${it.attempts}")
        }
    }

    fun markVerified(now: Long) {
        steps.lastOrNull()?.let {
            it.verified = true
            it.completedAt = now
            println("VASU_WORKFLOW_STEP step=${it.index} verified=true")
        }
    }

    fun markFailed(reason: String) {
        steps.lastOrNull()?.failureReason = reason
        workflowFailed = true
        workflowCompleted = false
        failureReason = reason
        println("VASU_WORKFLOW_STEP step=$currentStepIndex verified=false reason=$reason")
    }

    fun complete() {
        workflowCompleted = true
        workflowFailed = false
        println("VASU_WORKFLOW_STATE completed=true steps=${steps.size}")
    }

    fun fail(reason: String) {
        workflowFailed = true
        workflowCompleted = false
        failureReason = reason
        println("VASU_WORKFLOW_STATE failed=true reason=$reason")
    }

    fun snapshot(): List<StepRecord> = steps.map { it.copy() }

    fun reset() {
        steps.clear()
        currentStepIndex = -1
        workflowStarted = false
        workflowCompleted = false
        workflowFailed = false
        failureReason = null
    }
}
