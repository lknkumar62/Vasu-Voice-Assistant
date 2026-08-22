package com.vasu.ai.core

class VasuExecutionEngine(
    private val executor: VasuActionExecutor,
    private val screenDetector: VasuScreenTransitionDetector = VasuScreenTransitionDetector(),
    private val verifier: VasuActionVerifier = VasuActionVerifier(screenDetector),
    private val workflowReliability: VasuWorkflowReliability = VasuWorkflowReliability(),
    private val navigationRecovery: VasuNavigationRecovery = VasuNavigationRecovery()
) {

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
        val results = mutableListOf<StepResult>()

        for (action in actions) {
            val before = screenDetector.capture()
            val signature = buildActionSignature(action, before?.packageName)

            if (workflowReliability.isDuplicate(signature)) {
                results += StepResult(
                    action = action,
                    success = false,
                    attempts = 0,
                    verificationReason = "duplicate_action_blocked"
                )
                break
            }

            workflowReliability.recordExecution(signature)

            val firstAttempt = runCatching { executor.execute(action) }.getOrDefault(false)
            if (firstAttempt) workflowReliability.boundedSleep(120L)

            val after = screenDetector.capture()

            if (!firstAttempt) {
                if (isSafeToRetry(action)) {
                    val retryState = VasuWorkflowReliability.RetryState()
                    if (workflowReliability.shouldRetry(retryState, action::class.simpleName ?: "Unknown")) {
                        workflowReliability.boundedSleep(150L)
                        val retryBefore = screenDetector.capture()
                        val retrySignature = buildActionSignature(action, retryBefore?.packageName)

                        if (workflowReliability.isDuplicate(retrySignature)) {
                            results += StepResult(
                                action = action,
                                success = false,
                                attempts = 1,
                                verificationReason = "duplicate_retry_blocked"
                            )
                            break
                        }

                        workflowReliability.recordExecution(retrySignature)
                        val recovered = runCatching { executor.execute(action) }.getOrDefault(false)
                        if (recovered) workflowReliability.boundedSleep(120L)
                        val retryAfter = screenDetector.capture()

                        if (recovered) {
                            val verification = verifier.verify(action, retryBefore, retryAfter)
                            val success = verification.status != VasuActionVerifier.Status.NOT_VERIFIED
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

                results += StepResult(
                    action = action,
                    success = false,
                    verificationReason = "executor_failed"
                )
                break
            }

            val verification = verifier.verify(action, before, after)
            val success = verification.status != VasuActionVerifier.Status.NOT_VERIFIED

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

        return ExecutionResult(results)
    }

    private fun recoverNavigation(action: VasuAction, expectedPackage: String?) {
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
