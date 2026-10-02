package com.vasu.assistant.core.automation

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

interface StepExecutor {
    suspend fun executeStep(step: MissionStep): ActionResult
}

data class MissionStep(
    val action: String,
    val parameters: Map<String, Any> = emptyMap(),
    val params: Map<String, Any> = emptyMap(),
    val description: String = "",
    val timeoutMs: Long? = null,
    val continueOnError: Boolean = false,
    val requiresPermission: String? = null
) {
    fun getParam(key: String): Any? = parameters[key] ?: params[key]
}

data class Mission(
    val id: String = System.currentTimeMillis().toString(),
    val name: String,
    val steps: List<MissionStep>,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Singleton
class MissionEngine @Inject constructor(
    private val taskExecutor: StepExecutor
) {
    private val _missions = kotlinx.coroutines.flow.MutableStateFlow<List<Mission>>(emptyList())
    val missions: kotlinx.coroutines.flow.StateFlow<List<Mission>> = _missions

    var permissionChecker: (String) -> Boolean = { false }

    suspend fun executeMission(
        mission: Mission,
        missionTimeoutMs: Long = DEFAULT_MISSION_TIMEOUT_MS,
        stepTimeoutMs: Long = DEFAULT_STEP_TIMEOUT_MS
    ): ActionResult {
        if (mission.steps.size > MAX_STEPS) {
            return ActionResult.error(
                "run_mission",
                "Mission '${mission.name}' rejected: ${mission.steps.size} steps exceeds MAX_STEPS=$MAX_STEPS",
                "TOO_MANY_STEPS",
                data = mapOf("stepsCompleted" to 0, "stepsTotal" to mission.steps.size)
            )
        }
        var stepsCompleted = 0
        return try {
            withTimeoutOrNull(missionTimeoutMs) {
                mission.steps.forEachIndexed { index, step ->
                    currentCoroutineContext().ensureActive()
                    // Guard against run_mission -> executeTool -> executeMission recursion.
                    if (step.action == "run_mission") {
                        throw MissionAborted(
                            "Mission '${mission.name}' failed at step ${index + 1}",
                            "Nested missions are not allowed",
                            stepsCompleted
                        )
                    }
                    val required = step.requiresPermission
                    if (required != null && !permissionChecker(required)) {
                        val reason = "missing permission: $required"
                        if (!step.continueOnError) {
                            throw MissionAborted(
                                "Step ${index + 1}/${mission.steps.size} failed: ${step.action} — $reason",
                                "PRECONDITION_FAILED",
                                stepsCompleted
                            )
                        }
                        stepsCompleted++
                        return@forEachIndexed
                    }
                    val effectiveStepTimeout = step.timeoutMs ?: stepTimeoutMs
                    val result = withTimeoutOrNull(effectiveStepTimeout) {
                        taskExecutor.executeStep(step)
                    }
                    if (result == null) {
                        val reason = "timeout after ${effectiveStepTimeout}ms"
                        if (!step.continueOnError) {
                            throw MissionAborted(
                                "Step ${index + 1}/${mission.steps.size} failed: ${step.action} — $reason",
                                "TIMEOUT",
                                stepsCompleted
                            )
                        }
                        stepsCompleted++
                        return@forEachIndexed
                    }
                    if (!result.success) {
                        if (!step.continueOnError) {
                            throw MissionAborted(
                                "Step ${index + 1}/${mission.steps.size} failed: ${step.action} — ${result.error ?: result.message}",
                                result.error ?: "STEP_FAILED",
                                stepsCompleted
                            )
                        }
                        stepsCompleted++
                        return@forEachIndexed
                    }
                    stepsCompleted++
                }
                ActionResult.success(
                    "run_mission",
                    "Mission '${mission.name}' completed (${mission.steps.size} steps)",
                    data = mapOf("stepsCompleted" to stepsCompleted, "stepsTotal" to mission.steps.size)
                )
            } ?: ActionResult.error(
                "run_mission",
                "Mission '${mission.name}' timed out after ${missionTimeoutMs}ms ($stepsCompleted/${mission.steps.size} steps completed)",
                "MISSION_TIMEOUT",
                data = mapOf("stepsCompleted" to stepsCompleted, "stepsTotal" to mission.steps.size)
            )
        } catch (ce: CancellationException) {
            val job = currentCoroutineContext()[Job]
            if (job != null && !job.isActive) {
                throw ce
            }
            ActionResult.error(
                "run_mission",
                "Mission '${mission.name}' cancelled",
                "CANCELLED",
                data = mapOf(
                    "stepsCompleted" to stepsCompleted,
                    "stepsTotal" to mission.steps.size
                )
            )
        } catch (aborted: MissionAborted) {
            ActionResult.error(
                "run_mission",
                aborted.message ?: "Mission '${mission.name}' aborted",
                aborted.reason,
                data = mapOf("stepsCompleted" to aborted.stepsCompleted, "stepsTotal" to mission.steps.size)
            )
        }
    }

    suspend fun executeMission(missionId: String): ActionResult {
        val mission = _missions.value.find { it.id == missionId }
            ?: _missions.value.find { it.name.equals(missionId, ignoreCase = true) }
            ?: return ActionResult.error("run_mission", "Mission not found", "No mission matches '$missionId'")
        return executeMission(mission)
    }

    fun createMission(name: String, steps: List<MissionStep>): ActionResult {
        if (steps.size > MAX_STEPS) {
            return ActionResult.error(
                "create_mission",
                "Mission '$name' rejected: ${steps.size} steps exceeds MAX_STEPS=$MAX_STEPS",
                "TOO_MANY_STEPS",
                data = mapOf("stepsCompleted" to 0, "stepsTotal" to steps.size)
            )
        }
        val mission = Mission(name = name, steps = steps)
        _missions.value = _missions.value + mission
        return ActionResult.success("create_mission", "Mission '$name' created")
    }

    private class MissionAborted(
        message: String,
        val reason: String,
        val stepsCompleted: Int
    ) : RuntimeException(message)

    companion object {
        const val MAX_STEPS = 50
        const val DEFAULT_MISSION_TIMEOUT_MS = 60_000L
        const val DEFAULT_STEP_TIMEOUT_MS = 10_000L
    }
}

@Singleton
class MacroEngine @Inject constructor() {
    companion object {
        const val MAX_STEPS = MissionEngine.MAX_STEPS
    }
    fun createMacro(name: String, trigger: String, steps: List<MissionStep>): ActionResult {
        if (steps.size > MAX_STEPS) {
            android.util.Log.w("MacroEngine", "Macro '$name' rejected: ${steps.size} steps exceeds MAX_STEPS=$MAX_STEPS")
            return ActionResult.error(
                "create_macro",
                "Macro '$name' rejected: ${steps.size} steps exceeds MAX_STEPS=$MAX_STEPS",
                "TOO_MANY_STEPS",
                data = mapOf("stepsCompleted" to 0, "stepsTotal" to steps.size)
            )
        }
        android.util.Log.i("MacroEngine", "Macro created: $name with trigger: $trigger")
        return ActionResult.success("create_macro", "Macro '$name' created")
    }
}
