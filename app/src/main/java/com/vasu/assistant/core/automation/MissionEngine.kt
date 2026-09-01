package com.vasu.assistant.core.automation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Mission(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MissionStep> = emptyList(),
    val status: MissionStatus = MissionStatus.CREATED,
    val currentStep: Int = 0
)

data class MissionStep(
    val action: String,
    val parameters: Map<String, Any> = emptyMap(),
    val id: String = UUID.randomUUID().toString(),
    val params: Map<String, String> = emptyMap(),
    val timeout: Long = 10000L,
    val retryCount: Int = 2,
    val requireConfirmation: Boolean = false,
    val description: String = ""
)

enum class MissionStatus {
    CREATED, RUNNING, COMPLETED, FAILED
}

@Singleton
class MissionEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taskExecutor: TaskExecutor
) {
    private val _missions = MutableStateFlow<List<Mission>>(emptyList())
    val missions: StateFlow<List<Mission>> = _missions.asStateFlow()

    fun createMission(name: String, steps: List<MissionStep>): Mission {
        val mission = Mission(
            id = UUID.randomUUID().toString(),
            name = name,
            steps = steps
        )
        _missions.value = _missions.value + mission
        return mission
    }

    suspend fun executeMission(mission: Mission): Boolean {
        var current = mission.copy(status = MissionStatus.RUNNING)
        updateMission(current)

        return try {
            for ((index, step) in current.steps.withIndex()) {
                current = current.copy(currentStep = index)
                updateMission(current)
                val result = taskExecutor.executeStep(step)
                if (!result.success) {
                    current = current.copy(status = MissionStatus.FAILED)
                    updateMission(current)
                    return false
                }
            }
            current = current.copy(status = MissionStatus.COMPLETED)
            updateMission(current)
            true
        } catch (e: Exception) {
            current = current.copy(status = MissionStatus.FAILED)
            updateMission(current)
            false
        }
    }

    suspend fun executeMission(missionId: String): Boolean {
        val mission = getMission(missionId) ?: return false
        return executeMission(mission)
    }

    private fun updateMission(mission: Mission) {
        _missions.value = _missions.value.map { if (it.id == mission.id) mission else it }
    }

    fun getMission(id: String): Mission? = _missions.value.find { it.id == id }
}
