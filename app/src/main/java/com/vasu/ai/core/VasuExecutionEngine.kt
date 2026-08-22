package com.vasu.ai.core

import android.os.SystemClock
import java.util.UUID

class VasuExecutionEngine(
    private val executor: VasuActionExecutor,
    private val screenDetector: VasuScreenTransitionDetector = VasuScreenTransitionDetector(),
    private val verifier: VasuActionVerifier = VasuActionVerifier(screenDetector),
    private val workflowReliability: VasuWorkflowReliability = VasuWorkflowReliability(),
    private val navigationRecovery: VasuNavigationRecovery = VasuNavigationRecovery(),
    private val timeoutController: VasuTimeoutController = VasuTimeoutController(),
    private val retryPolicy: VasuRetryPolicy = VasuRetryPolicy(),
    private val completionDetector: VasuWorkflowCompletionDetector = VasuWorkflowCompletionDetector(),
    private val replanLoopGuard: VasuReplanLoopGuard = VasuReplanLoopGuard()
) {
    private val workflowState = VasuWorkflowState()
    private var workflowContext: VasuWorkflowContext? = null
    private var workflowDeadline: VasuTimeoutController.Deadline? = null

    data class StepResult(
        val action: VasuAction,
        val success: Boolean,
        val recovered: Boolean = false,
        val attempts: Int = 1,
        val verification: VasuActionVerifier.Status = VasuActionVerifier.Status.UNKNOWN,
        val verificationReason: String = ""
    )

    data class ExecutionResult(
        val steps: List<StepResult>
    ) {
        val success: Boolean get() = steps.isNotEmpty() && steps.all { it.success }
        val completedCount: Int get() = steps.count { it.success }
        val recoveredCount: Int get() = steps.count { it.recovered }
        val failedAction: VasuAction? get() = steps.lastOrNull { !it.success }?.action
        val totalAttempts: Int get() = steps.sumOf { it.attempts }
    }

    fun execute(actions: List<VasuAction>): ExecutionResult {
        if (actions.isEmpty()) return ExecutionResult(emptyList())

        ensureWorkflowStarted()
        val results = mutableListOf<StepResult>()

        for (action in actions) {
            if (workflowDeadline?.expired() == true) {
                val reason = "workflow_timeout"
                workflowState.fail(reason)
                workflowContext?.lastFailureReason = reason
                println("VASU_WORKFLOW_TIMEOUT")
                results += StepResult(action = action, success = false, verificationReason = reason)
                break
            }

            val actionTimeout = when (action) {
                is VasuAction.OpenApp -> VasuTimeoutController.OPEN_APP_TIMEOUT_MS
                else -> VasuTimeoutController.DEFAULT_ACTION_TIMEOUT_MS
            }
            val deadline = timeoutController.start(actionTimeout)

            val stepIndex = workflowState.currentStepIndex + 1
            workflowState.beginStep(
                index = stepIndex,
                actionType = action::class.simpleName ?: "Unknown",
                now = SystemClock.uptimeMillis()
            )
            workflowState.recordAttempt()
            workflowContext?.let {
                it.stepIndex = stepIndex
                it.lastActionType = action::class.simpleName
            }

            val before = screenDetector.capture()
            workflowContext?.let {
                it.previousScreenFingerprint = it.currentScreenFingerprint
                it.currentScreenFingerprint = before?.fingerprint
                it.currentPackage = before?.packageName
            }

            val signature = buildActionSignature(action, before?.packageName)
            if (workflowReliability.isDuplicate(signature)) {
                val reason = "duplicate_action_blocked"
                workflowState.markFailed(reason)
                workflowContext?.let {
                    it.lastActionVerified = false
                    it.lastFailureReason = reason
                }
                results += StepResult(action = action, success = false, attempts = 0, verificationReason = reason)
                break
            }

            workflowReliability.recordExecution(signature)
            val firstAttempt = runCatching { executor.execute(action) }.getOrDefault(false)

            if (deadline.expired()) {
                val reason = "action_timeout"
                workflowState.markFailed(reason)
                workflowContext?.lastFailureReason = reason
                println(
                    "VASU_TIMEOUT action=${action::class.simpleName} timeoutMs=$actionTimeout"
                )
                results += StepResult(action = action, success = false, verificationReason = reason)
                break
            }

            if (firstAttempt) workflowReliability.boundedSleep(120L)
            val after = screenDetector.capture()

            if (!firstAttempt) {
                val attemptsSoFar = workflowState.snapshot().lastOrNull()?.attempts ?: 1
                val retryAllowed =
                    isSafeToRetry(action) && retryPolicy.canRetry(action, attemptsSoFar)

                if (retryAllowed && !deadline.expired()) {
                    val retryState = VasuWorkflowReliability.RetryState(
                        attempt = attemptsSoFar,
                        maxAttempts = retryPolicy.maxRetriesFor(action)
                    )
                    if (workflowReliability.shouldRetry(retryState, action::class.simpleName ?: "Unknown")) {
                        workflowReliability.boundedSleep(150L)
                        val retryBefore = screenDetector.capture()
                        val retrySignature = buildActionSignature(action, retryBefore?.packageName)

                        if (workflowReliability.isDuplicate(retrySignature)) {
                            val reason = "duplicate_retry_blocked"
                            workflowState.markFailed(reason)
                            workflowContext?.let {
                                it.lastActionVerified = false
                                it.lastFailureReason = reason
                            }
                            results += StepResult(action = action, success = false, attempts = attemptsSoFar, verificationReason = reason)
                            break
                        }

                        workflowReliability.recordExecution(retrySignature)
                        workflowState.recordAttempt()
                        val recovered = runCatching { executor.execute(action) }.getOrDefault(false)

                        if (SystemClock.uptimeMillis() - deadline.startedAt >= actionTimeout) {
                            val reason = "action_timeout"
                            workflowState.markFailed(reason)
                            workflowContext?.lastFailureReason = reason
                            println(
                                "VASU_TIMEOUT action=${action::class.simpleName} timeoutMs=$actionTimeout"
                            )
                            results += StepResult(
                                action = action,
                                success = false,
                                attempts = workflowState.snapshot().lastOrNull()?.attempts ?: 2,
                                verificationReason = reason
                            )
                            break
                        }

                        if (recovered) workflowReliability.boundedSleep(120L)
                        val retryAfter = screenDetector.capture()

                        if (recovered) {
                            val verification = verifier.verify(action, retryBefore, retryAfter)
                            val success = verification.status != VasuActionVerifier.Status.NOT_VERIFIED
                            recordVerification(action, success, verification)
                            results += StepResult(
                                action = action,
                                success = success,
                                recovered = true,
                                attempts = 2,
                                verification = verification.status,
                                verificationReason = verification.reason
                            )
                            logVerification(action, 2, verification)

                            if (!success) {
                                recoverNavigation(action, retryAfter?.packageName)
                                break
                            }
                            continue
                        }
                    }
                }

                val reason =
                    if (!retryPolicy.canRetry(action, attemptsSoFar)) "retry_budget_exhausted" else "executor_failed"
                workflowState.markFailed(reason)
                workflowContext?.let {
                    it.lastActionVerified = false
                    it.lastFailureReason = reason
                }
                println(
                    "VASU_RETRY_POLICY allowed=false action=${action::class.simpleName} attempts=$attemptsSoFar"
                )
                results += StepResult(action = action, success = false, verificationReason = reason)
                break
            }

            val verification = verifier.verify(action, before, after)
            val success = verification.status != VasuActionVerifier.Status.NOT_VERIFIED
            recordVerification(action, success, verification)
            results += StepResult(
                action = action,
                success = success,
                verification = verification.status,
                verificationReason = verification.reason
            )
            logVerification(action, 1, verification)

            if (!success) {
                recoverNavigation(action, after?.packageName)
                break
            }
        }

        workflowContext?.currentPackage = screenDetector.capture()?.packageName
        return ExecutionResult(results)
    }

    fun workflowSnapshot(): List<VasuWorkflowState.StepRecord> = workflowState.snapshot()

    fun evaluateWorkflowCompletion(expectedStepCount: Int): VasuWorkflowCompletionDetector.CompletionResult {
        if (expectedStepCount <= 0) {
            return VasuWorkflowCompletionDetector.CompletionResult(false, "empty_expected_plan")
        }
        val snapshot = workflowState.snapshot()
        if (snapshot.size < expectedStepCount) {
            return VasuWorkflowCompletionDetector.CompletionResult(false, "steps_incomplete")
        }
        return completionDetector.evaluate(workflowState)
    }

    fun shouldStopReplan(actionType: String): Boolean =
        replanLoopGuard.shouldStop(workflowState.currentStepIndex, actionType)

    fun resetReplanLoopGuard() = replanLoopGuard.reset()

    fun completeWorkflow() {
        workflowState.complete()
        workflowContext = null
        workflowDeadline = null
        replanLoopGuard.reset()
    }

    fun failWorkflow(reason: String) {
        workflowState.fail(reason)
        workflowContext?.lastFailureReason = reason
    }

    private fun ensureWorkflowStarted() {
        if (!workflowState.workflowStarted || workflowState.workflowCompleted || workflowState.workflowFailed) {
            workflowState.start()
            workflowContext = VasuWorkflowContext(
                workflowId = UUID.randomUUID().toString(),
                originalCommand = "runtime_workflow"
            )
            workflowDeadline = timeoutController.start(VasuTimeoutController.WORKFLOW_TIMEOUT_MS)
            replanLoopGuard.reset()
        }
    }

    private fun recordVerification(
        action: VasuAction,
        success: Boolean,
        verification: VasuActionVerifier.VerificationResult
    ) {
        if (success) {
            workflowState.markVerified(SystemClock.uptimeMillis())
        } else {
            workflowState.markFailed(verification.reason)
        }
        workflowContext?.let {
            it.lastActionType = action::class.simpleName
            it.lastActionVerified = success
            it.lastFailureReason = if (success) null else verification.reason
        }
    }

    private fun recoverNavigation(action: VasuAction, expectedPackage: String?) {
        if (!VasuWorkflowStepGuard.isRecoverable(action, "verification_failed")) {
            println("VASU_WORKFLOW recovery_blocked=true")
            return
        }
        if (isSensitiveAction(action)) {
            println("VASU_WORKFLOW sensitive_action_recovery_blocked=true")
            return
        }
        val recovery = navigationRecovery.recover(expectedPackage)
        println("VASU_WORKFLOW navigation_recovery=${recovery.recovered}")
    }

    private fun buildActionSignature(
        action: VasuAction,
        packageName: String?
    ): VasuWorkflowReliability.ActionSignature = when (action) {
        is VasuAction.OpenApp -> VasuWorkflowReliability.ActionSignature("OpenApp", action.packageName, null, packageName)
        is VasuAction.TypeText -> VasuWorkflowReliability.ActionSignature("TypeText", null, action.text, packageName)
        VasuAction.ClearText -> VasuWorkflowReliability.ActionSignature("ClearText", null, null, packageName)
        is VasuAction.ClickText -> VasuWorkflowReliability.ActionSignature("ClickText", action.text, null, packageName)
        is VasuAction.LongClickText -> VasuWorkflowReliability.ActionSignature("LongClickText", action.text, null, packageName)
        is VasuAction.ClickDescription -> VasuWorkflowReliability.ActionSignature("ClickDescription", action.description, null, packageName)
        is VasuAction.ClickViewId -> VasuWorkflowReliability.ActionSignature("ClickViewId", action.viewId, null, packageName)
        else -> VasuWorkflowReliability.ActionSignature(action::class.simpleName ?: "Unknown", null, null, packageName)
    }

    private fun isSensitiveAction(action: VasuAction): Boolean = when (action) {
        is VasuAction.CallContact,
        is VasuAction.SendSms -> true
        else -> false
    }

    private fun logVerification(
        action: VasuAction,
        attempt: Int,
        verification: VasuActionVerifier.VerificationResult
    ) {
        println(
            "VASU_ACTION_VERIFICATION " +
                "action=${action::class.simpleName} " +
                "attempt=$attempt " +
                "status=${verification.status} " +
                "reason=${verification.reason}"
        )
    }

    private fun isSafeToRetry(action: VasuAction): Boolean = when (action) {
        is VasuAction.OpenApp,
        VasuAction.OpenWifiSettings,
        VasuAction.OpenBluetoothSettings,
        VasuAction.OpenBrightnessSettings,
        VasuAction.OpenDndSettings,
        VasuAction.OpenAirplaneModeSettings,
        VasuAction.OpenBatterySaverSettings,
        VasuAction.OpenLocationSettings -> true
        else -> false
    }
}
