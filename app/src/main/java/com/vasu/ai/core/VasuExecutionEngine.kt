package com.vasu.ai.core

class VasuExecutionEngine(private val executor: VasuActionExecutor) {

    data class StepResult(
        val action: VasuAction,
        val success: Boolean,
        val recovered: Boolean = false
    )

    data class ExecutionResult(
        val steps: List<StepResult>
    ) {
        val success: Boolean get() = steps.isNotEmpty() && steps.all { it.success }
        val completedCount: Int get() = steps.count { it.success }
        val recoveredCount: Int get() = steps.count { it.recovered }
    }

    fun execute(actions: List<VasuAction>): ExecutionResult {
        val results = mutableListOf<StepResult>()
        for (action in actions) {
            val firstAttempt = runCatching { executor.execute(action) }.getOrDefault(false)
            if (firstAttempt) {
                results += StepResult(action, true)
                continue
            }

            // Only retry actions whose effect is idempotent. Never blindly retry clicks,
            // typing, calls, SMS, gestures, or other potentially duplicated side effects.
            if (isSafeToRetry(action)) {
                Thread.sleep(150L)
                val recovered = runCatching { executor.execute(action) }.getOrDefault(false)
                results += StepResult(action, recovered, recovered)
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
