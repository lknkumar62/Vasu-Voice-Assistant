package com.vasu.assistant.core.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class AgentRunner(
    val maxStepsPerTask: Int = 8,
    val taskTimeoutMs: Long = 30_000,
    val stepTimeoutMs: Long = 10_000,
) {

    data class StepSpec<T>(val id: String, val timeoutMs: Long? = null)

    data class RunResult<T>(
        val success: Boolean,
        val completedIds: List<String>,
        val stepsRun: Int,
        val lastErrorKind: String? = null,
        val lastErrorMessage: String? = null,
        val timedOutAtStepId: String? = null,
    )

    suspend fun <T> run(
        steps: List<StepSpec<T>>,
        stepExec: suspend (StepSpec<T>) -> T
    ): RunResult<T> {
        if (steps.size > maxStepsPerTask) {
            return RunResult(
                success = false,
                completedIds = emptyList(),
                stepsRun = 0,
                lastErrorKind = TOO_MANY_STEPS,
                lastErrorMessage = "step count ${steps.size} exceeds maxStepsPerTask $maxStepsPerTask",
            )
        }

        val completed = mutableListOf<String>()
        var stepsRun = 0
        var errorKind: String? = null
        var errorMessage: String? = null
        var timedOutAt: String? = null
        var inFlightStepId: String? = null

        val finished = withTimeoutOrNull(taskTimeoutMs) {
            for (step in steps) {
                inFlightStepId = step.id
                stepsRun++
                val effectiveTimeout = step.timeoutMs ?: stepTimeoutMs
                val output = try {
                    withTimeoutOrNull(effectiveTimeout) { stepExec(step) }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    errorKind = EXCEPTION
                    errorMessage = e.message ?: e::class.simpleName
                    return@withTimeoutOrNull
                }
                if (output == null) {
                    errorKind = STEP_TIMEOUT
                    errorMessage = "step '${step.id}' timed out after ${effectiveTimeout}ms"
                    timedOutAt = step.id
                    return@withTimeoutOrNull
                }
                completed += step.id
            }
        }

        return when {
            finished == null -> RunResult(
                success = false,
                completedIds = completed.toList(),
                stepsRun = stepsRun,
                lastErrorKind = TASK_TIMEOUT,
                lastErrorMessage = "task timed out after ${taskTimeoutMs}ms",
                timedOutAtStepId = inFlightStepId,
            )
            errorKind != null -> RunResult(
                success = false,
                completedIds = completed.toList(),
                stepsRun = stepsRun,
                lastErrorKind = errorKind,
                lastErrorMessage = errorMessage,
                timedOutAtStepId = timedOutAt,
            )
            else -> RunResult(
                success = true,
                completedIds = completed.toList(),
                stepsRun = stepsRun,
            )
        }
    }

    companion object {
        const val STEP_TIMEOUT = "STEP_TIMEOUT"
        const val TASK_TIMEOUT = "TASK_TIMEOUT"
        const val EXCEPTION = "EXCEPTION"
        const val TOO_MANY_STEPS = "TOO_MANY_STEPS"
    }
}
