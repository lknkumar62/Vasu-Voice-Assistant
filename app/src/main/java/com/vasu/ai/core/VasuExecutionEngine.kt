package com.vasu.ai.core

class VasuExecutionEngine(
    private val executor: VasuActionExecutor,
    private val screenDetector: VasuScreenTransitionDetector = VasuScreenTransitionDetector(),
    private val verifier: VasuActionVerifier = VasuActionVerifier(screenDetector)
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

            val firstAttempt = runCatching {
                executor.execute(action)
            }.getOrDefault(false)

            if (firstAttempt) {
                Thread.sleep(120L)
            }

            val after = screenDetector.capture()

            if (!firstAttempt) {
                if (isSafeToRetry(action)) {
                    Thread.sleep(150L)

                    val retryBefore = screenDetector.capture()
                    val recovered = runCatching {
                        executor.execute(action)
                    }.getOrDefault(false)

                    if (recovered) {
                        Thread.sleep(120L)
                    }

                    val retryAfter = screenDetector.capture()

                    if (recovered) {
                        val verification = verifier.verify(
                            action = action,
                            before = retryBefore,
                            after = retryAfter
                        )
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

                        if (!success) break
                        continue
                    }

                    results += StepResult(
                        action = action,
                        success = false,
                        recovered = false,
                        attempts = 2,
                        verification = VasuActionVerifier.Status.UNKNOWN,
                        verificationReason = "executor_failed_after_safe_retry"
                    )
                    break
                }

                results += StepResult(
                    action = action,
                    success = false,
                    recovered = false,
                    attempts = 1,
                    verification = VasuActionVerifier.Status.UNKNOWN,
                    verificationReason = "executor_failed"
                )
                break
            }

            val verification = verifier.verify(
                action = action,
                before = before,
                after = after
            )

            val success = verification.status != VasuActionVerifier.Status.NOT_VERIFIED

            results += StepResult(
                action = action,
                success = success,
                recovered = false,
                attempts = 1,
                verification = verification.status,
                verificationReason = verification.reason
            )

            logVerification(action, 1, verification)

            if (!success) break
        }

        return ExecutionResult(results)
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
