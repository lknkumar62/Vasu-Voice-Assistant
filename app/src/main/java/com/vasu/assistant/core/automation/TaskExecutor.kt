package com.vasu.assistant.core.automation

import com.vasu.assistant.core.ai.ToolRouter
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TaskExecutor @Inject constructor(
    private val toolRouterProvider: Provider<ToolRouter>
) {
    private val toolRouter: ToolRouter
        get() = toolRouterProvider.get()

    suspend fun executeStep(step: MissionStep): ActionResult {
        val params = if (step.parameters.isNotEmpty()) step.parameters else step.params
        // Alias legacy mission actions onto real tool names so every step goes
        // through ToolRouter's risk gate (no direct accessibility bypass).
        val toolAction = when (step.action) {
            "type" -> "type_text"
            "back" -> "press_back"
            "home" -> "press_home"
            else -> step.action
        }
        return when (toolAction) {
            "wait" -> {
                val ms = (params["duration"] as? Number)?.toLong() ?: (params["delay"] as? Number)?.toLong() ?: 2000L
                delay(ms)
                ActionResult.success("wait", "Waited ${ms}ms")
            }
            "delay" -> {
                val ms = (params["milliseconds"] as? Number)?.toLong() ?: (params["duration"] as? Number)?.toLong() ?: 1000L
                delay(ms)
                ActionResult.success("delay", "Delayed ${ms}ms")
            }
            else -> {
                val toolParams = params.toMutableMap()
                toolParams["action"] = step.action
                // Legacy mission steps used package_name; open_app reads package/app/name.
                val legacyPkg = toolParams["package_name"]
                if (legacyPkg != null && !toolParams.containsKey("package")) {
                    toolParams["package"] = legacyPkg
                }
                toolRouter.executeTool(toolAction, toolParams)
            }
        }
    }
}
