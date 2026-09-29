package com.vasu.assistant.core.automation

import javax.inject.Inject
import javax.inject.Singleton

data class MissionStep(
    val action: String,
    val parameters: Map<String, Any> = emptyMap(),
    val params: Map<String, Any> = emptyMap(),
    val description: String = ""
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
    private val taskExecutor: TaskExecutor
) {
    private val _missions = kotlinx.coroutines.flow.MutableStateFlow<List<Mission>>(emptyList())
    val missions: kotlinx.coroutines.flow.StateFlow<List<Mission>> = _missions

    suspend fun executeMission(mission: Mission): ActionResult {
        mission.steps.forEachIndexed { index, step ->
            // Guard against run_mission -> executeTool -> executeMission recursion.
            if (step.action == "run_mission") {
                return ActionResult.error(
                    "run_mission",
                    "Mission '${mission.name}' failed at step ${index + 1}",
                    "Nested missions are not allowed"
                )
            }
            val result = taskExecutor.executeStep(step)
            if (!result.success) {
                return ActionResult.error(
                    "run_mission",
                    "Mission '${mission.name}' failed at step ${index + 1}: ${step.action}",
                    result.error ?: result.message
                )
            }
        }
        return ActionResult.success(
            "run_mission",
            "Mission '${mission.name}' completed (${mission.steps.size} steps)"
        )
    }

    suspend fun executeMission(missionId: String): ActionResult {
        val mission = _missions.value.find { it.id == missionId }
            ?: _missions.value.find { it.name.equals(missionId, ignoreCase = true) }
            ?: return ActionResult.error("run_mission", "Mission not found", "No mission matches '$missionId'")
        return executeMission(mission)
    }

    fun createMission(name: String, steps: List<MissionStep>) {
        val mission = Mission(name = name, steps = steps)
        _missions.value = _missions.value + mission
    }
}

@Singleton
class MacroEngine @Inject constructor() {
    fun createMacro(name: String, trigger: String, steps: List<MissionStep>) {
        android.util.Log.i("MacroEngine", "Macro created: $name with trigger: $trigger")
    }
}
