package com.vasu.ai.core

class VasuExecutionEngine(
    private val executor: VasuActionExecutor,
    private val screenDetector: VasuScreenTransitionDetector =
        VasuScreenTransitionDetector()
) {

    data class StepResult(
        val action: VasuAction,
        val success: Boolean,
        val recovered: Boolean = false,
        val attempts: Int = 1
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
            /*
             * Capture the UI before the action.
             *
             * This is observation only. We do not treat a screen change
             * as proof that the action succeeded yet.
             */
            val before = screenDetector.capture()

            val firstAttempt = runCatching { executor.execute(action) }.getOrDefault(false)

            /*
             * Give Android Accessibility a short opportunity to publish
             * the resulting UI tree.
             */
            if (firstAttempt) {
                Thread.sleep(120L)
            }

            val after = screenDetector.capture()

            val screenChanged = screenDetector.hasChanged(
                before = before,
                after = after
            )

            if (firstAttempt) {
                println(
                    "VASU_SCREEN_TRANSITION " +
                        "action=${action::class.simpleName} " +
                        "changed=$screenChanged " +
                        "before=${before?.fingerprint?.take(8)} " +
                        "after=${after?.fingerprint?.take(8)}"
                )

                /*
                 * The executor accepted the action.
                 *
                 * screenChanged is deliberately not used as the success
                 * decision yet. Some valid actions legitimately leave the
                 * accessibility tree unchanged.
                 *
                 * Step 2 will combine this signal with action-specific
                 * verification.
                 */
                results += StepResult(
                    action = action,
                    success = true,
                    recovered = false,
                    attempts = 1
                )

                continue
            }

            // Only retry actions whose effect is idempotent. Never blindly retry clicks,
            // typing, calls, SMS, gestures, or other potentially duplicated side effects.
            if (isSafeToRetry(action)) {
                Thread.sleep(150L)
                val recovered = runCatching { executor.execute(action) }.getOrDefault(false)
                results += StepResult(action, recovered, recovered, attempts = 2)
                if (!recovered) break
                continue
            }

            results += StepResult(action, false)
            break
        }
        return ExecutionResult(results)
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
