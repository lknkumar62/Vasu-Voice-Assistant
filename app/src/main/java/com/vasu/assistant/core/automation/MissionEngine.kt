package com.vasu.assistant.core.automation

import kotlinx.coroutines.delay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Mission(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MissionStep>,
    val createdAt: Long = System.currentTimeMillis(),
    var status: MissionStatus = MissionStatus.CREATED,
    var currentStep: Int = 0,
    val logs: MutableList<String> = mutableListOf()
)

data class MissionStep(
    val action: String,
    val parameters: Map<String, Any> = emptyMap(),
    val description: String = "",
    val timeout: Long = 10000L,
    val retryCount: Int = 2,
    val requireConfirmation: Boolean = false
)

enum class MissionStatus { CREATED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

@Singleton
class MissionEngine @Inject constructor(
    private val taskExecutor: TaskExecutor
) {
    private val missions = mutableMapOf<String, Mission>()
    private var runningMissionId: String? = null

    fun createMission(name: String, steps: List<MissionStep>): Mission {
        val mission = Mission(name = name, steps = steps)
        missions[mission.id] = mission
        return mission
    }

    suspend fun executeMission(missionId: String): ActionResult {
        val mission = missions[missionId] ?: return ActionResult.error("mission", "Mission not found", "No mission with id: $missionId")
        if (runningMissionId != null) return ActionResult.error("mission", "Another mission is running", "Mission $runningMissionId is active")

        runningMissionId = missionId
        mission.status = MissionStatus.RUNNING
        mission.logs.add("Mission '${mission.name}' started at ${System.currentTimeMillis()}")

        var stepsCompleted = 0
        for (i in mission.steps.indices) {
            val step = mission.steps[i]
            mission.currentStep = i
            mission.logs.add("Step ${i + 1}: ${step.description.ifEmpty { step.action }}")

            var attempts = 0
            var stepSuccess = false
            while (attempts <= step.retryCount && !stepSuccess) {
                try {
                    val result = taskExecutor.executeStep(step)
                    if (result.success) {
                        stepSuccess = true
                        stepsCompleted++
                        mission.logs.add("Step ${i + 1} completed: ${result.message}")
                    } else {
                        attempts++
                        mission.logs.add("Step ${i + 1} attempt $attempts failed: ${result.message}")
                        delay(1000)
                    }
                } catch (e: Exception) {
                    attempts++
                    mission.logs.add("Step ${i + 1} error: ${e.message}")
                }
            }
            if (!stepSuccess) {
                mission.status = MissionStatus.FAILED
                runningMissionId = null
                return ActionResult.error("mission", "Mission failed at step ${i + 1}", "Step '${step.action}' failed after ${step.retryCount + 1} attempts")
            }
        }

        mission.status = MissionStatus.COMPLETED
        mission.logs.add("Mission completed at ${System.currentTimeMillis()}")
        runningMissionId = null
        return ActionResult.success("mission", "Mission '${mission.name}' completed ($stepsCompleted/${mission.steps.size} steps)")
    }

    fun pauseMission(): ActionResult {
        val id = runningMissionId ?: return ActionResult.error("mission", "No running mission", "No active mission")
        missions[id]?.status = MissionStatus.PAUSED
        runningMissionId = null
        return ActionResult.success("mission", "Mission paused")
    }

    fun resumeMission(missionId: String): ActionResult {
        val mission = missions[missionId] ?: return ActionResult.error("mission", "Mission not found", "No mission: $missionId")
        if (mission.status != MissionStatus.PAUSED) return ActionResult.error("mission", "Mission is not paused", "Status: ${mission.status}")
        mission.status = MissionStatus.RUNNING
        return ActionResult.success("mission", "Mission resumed")
    }

    fun cancelMission(missionId: String): ActionResult {
        val mission = missions[missionId] ?: return ActionResult.error("mission", "Mission not found", "No mission: $missionId")
        mission.status = MissionStatus.CANCELLED
        if (runningMissionId == missionId) runningMissionId = null
        return ActionResult.success("mission", "Mission cancelled")
    }

    fun getMissionStatus(missionId: String): ActionResult {
        val mission = missions[missionId] ?: return ActionResult.error("mission", "Mission not found", "No mission: $missionId")
        return ActionResult.success("mission", mission.status.name, mapOf(
            "id" to mission.id, "name" to mission.name,
            "status" to mission.status.name,
            "currentStep" to mission.currentStep,
            "totalSteps" to mission.steps.size,
            "logs" to mission.logs
        ))
    }

    fun getActiveMissionId() = runningMissionId
}
