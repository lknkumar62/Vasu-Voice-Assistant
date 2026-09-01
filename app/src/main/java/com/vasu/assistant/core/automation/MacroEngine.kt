package com.vasu.assistant.core.automation

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val trigger: String,
    val actions: List<MissionStep>,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRun: Long = 0,
    val runCount: Int = 0
)

@Singleton
class MacroEngine @Inject constructor(
    private val missionEngine: MissionEngine,
    private val taskExecutor: TaskExecutor
) {
    private val macros = mutableMapOf<String, Macro>()

    fun createMacro(name: String, trigger: String, actions: List<MissionStep>): Macro {
        val macro = Macro(name = name, trigger = trigger, actions = actions)
        macros[macro.id] = macro
        return macro
    }

    fun deleteMacro(macroId: String): ActionResult {
        return if (macros.remove(macroId) != null) {
            ActionResult.success("macro", "Macro deleted")
        } else {
            ActionResult.error("macro", "Macro not found", "No macro: $macroId")
        }
    }

    fun toggleMacro(macroId: String): ActionResult {
        val macro = macros[macroId] ?: return ActionResult.error("macro", "Macro not found", "No macro: $macroId")
        val updated = macro.copy(enabled = !macro.enabled)
        macros[macroId] = updated
        return ActionResult.success("macro", "Macro ${if (updated.enabled) "enabled" else "disabled"}: ${updated.name}")
    }

    suspend fun runMacro(macroId: String): ActionResult {
        val macro = macros[macroId] ?: return ActionResult.error("macro", "Macro not found", "No macro: $macroId")
        if (!macro.enabled) return ActionResult.error("macro", "Macro is disabled: ${macro.name}", "Macro disabled")

        return try {
            val mission = missionEngine.createMission("Macro: ${macro.name}", macro.actions)
            val success = missionEngine.executeMission(mission)
            if (success) {
                macros[macroId] = macro.copy(lastRun = System.currentTimeMillis(), runCount = macro.runCount + 1)
                ActionResult.success("macro", "Macro executed successfully: ${macro.name}")
            } else {
                ActionResult.error("macro", "Macro execution failed: ${macro.name}", "Execution failed")
            }
        } catch (e: Exception) {
            ActionResult.error("macro", "Macro execution failed", e.message ?: "Unknown")
        }
    }

    fun matchTrigger(keyword: String): Macro? {
        return macros.values.find { it.enabled && it.trigger.equals(keyword, ignoreCase = true) }
    }

    fun listMacros(): ActionResult {
        val list = macros.values.map { m ->
            mapOf("id" to m.id, "name" to m.name, "trigger" to m.trigger,
                "enabled" to m.enabled, "actions" to m.actions.size, "runCount" to m.runCount)
        }
        return ActionResult.success("macros", "Found ${list.size} macros", mapOf("macros" to list))
    }

    fun getMacro(macroId: String): Macro? = macros[macroId]
}
