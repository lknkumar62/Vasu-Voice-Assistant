package com.vasu.assistant.core.automation

import com.vasu.assistant.accessibility.AccessibilityActions
import com.vasu.assistant.accessibility.VasuAccessibilityService
import com.vasu.assistant.core.ai.ToolRouter
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskExecutor @Inject constructor(
    private val toolRouter: ToolRouter
) {
    private fun getAccessibilityActions(): AccessibilityActions? {
        val service = VasuAccessibilityService.instance.value ?: return null
        return service.getInteractionManager().let {
            // Access actions through the service
            null
        }
        // Use the service directly
    }

    private fun withService(block: (VasuAccessibilityService) -> ActionResult): ActionResult {
        val service = VasuAccessibilityService.instance.value
            ?: return ActionResult.error("accessibility", "Accessibility service not running", "Service not connected")
        return block(service)
    }

    suspend fun executeStep(step: MissionStep): ActionResult {
        return when (step.action) {
            "open_app" -> {
                val pkg = step.parameters["package"] as? String ?: ""
                val result = toolRouter.executeTool("open_app", mapOf("package" to pkg))
                if (result.success) result else ActionResult.error("open_app", "Open app failed: $pkg", result.error ?: result.message)
            }
            "click" -> {
                val text = step.parameters["text"] as? String ?: ""
                withService { it.clickElement(text) }
            }
            "type" -> {
                val label = step.parameters["label"] as? String ?: ""
                val text = step.parameters["text"] as? String ?: ""
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
                val ms = (step.parameters["duration"] as? Number)?.toLong() ?: 2000L
                delay(ms)
                ActionResult.success("wait", "Waited ${ms}ms")
            }
            "delay" -> {
                val ms = (step.parameters["milliseconds"] as? Number)?.toLong() ?: 1000L
                delay(ms)
                ActionResult.success("delay", "Delayed ${ms}ms")
            }
            else -> {
                val params = step.parameters.toMutableMap()
                params["action"] = step.action
                toolRouter.executeTool(step.action, params)
            }
        }
    }
}
