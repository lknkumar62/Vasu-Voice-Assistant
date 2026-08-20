package com.vasu.ai.core

class VasuExecutionEngine(private val executor: VasuActionExecutor) {

    data class StepResult(
        val action: VasuAction,
        val success: Boolean
    )

    data class ExecutionResult(
        val steps: List<StepResult>
    ) {
        val success: Boolean get() = steps.isNotEmpty() && steps.all { it.success }
        val completedCount: Int get() = steps.count { it.success }
    }

    fun execute(actions: List<VasuAction>): ExecutionResult {
        val results = mutableListOf<StepResult>()
        for (action in actions) {
            val success = runCatching { executor.execute(action) }.getOrDefault(false)
            results += StepResult(action, success)
            if (!success) break
        }
        return ExecutionResult(results)
    }
}
