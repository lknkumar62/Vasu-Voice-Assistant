package com.vasu.assistant.core.automation

import com.vasu.assistant.accessibility.VasuAccessibilityService
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
    private fun withService(block: (VasuAccessibilityService) -> ActionResult): ActionResult {
        val service = VasuAccessibilityService.instance.value
            ?: return ActionResult.error("accessibility", "Accessibility service not running", "Service not connected")
        return block(service)
    }

    suspend fun executeStep(step: MissionStep): ActionResult {
        val params = if (step.parameters.isNotEmpty()) step.parameters else step.params
        return when (step.action) {
            "open_app" -> {
                val pkg = params["package"] as? String ?: params["package_name"] as? String ?: ""
                val result = toolRouter.executeTool("open_app", mapOf("package" to pkg))
                if (result.success) result else ActionResult.error("open_app", "Open app failed: $pkg", result.error ?: result.message)
            }
            "click" -> {
                val text = params["text"] as? String ?: ""
                withService { it.clickElement(text) }
            }
            "type" -> {
                val label = params["label"] as? String ?: ""
                val text = params["text"] as? String ?: ""
                withService { it.typeText(label, text) }
            }
            "read_screen" -> {
                withService { it.readScreen() }
            }
            "scroll_down" -> {
                withService { it.scrollDown() }
            }
            "scroll_up" -> {
                withService { it.scrollUp() }
            }
            "back" -> {
                withService { it.pressBack() }
            }
            "home" -> {
                withService { it.pressHome() }
            }
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
                toolRouter.executeTool(step.action, toolParams)
            }
        }
    }
}
