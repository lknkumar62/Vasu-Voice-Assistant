package com.vasu.assistant.core.automation

import com.vasu.assistant.accessibility.AccessibilityActions
import com.vasu.assistant.core.ai.ToolRouter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskExecutor @Inject constructor(
    private val toolRouter: ToolRouter,
    private val accessibilityActions: AccessibilityActions
) {
    suspend fun executeStep(step: MissionStep): ActionResult {
        return when (step.action) {
            "open_app" -> {
                val pkg = step.parameters["package"] as? String ?: ""
                val result = toolRouter.executeTool("open_app", mapOf("package" to pkg))
                verify(result, "Open app: $pkg")
            }
            "click" -> {
                val text = step.parameters["text"] as? String ?: ""
                val result = accessibilityActions.clickByText(text)
                if (result) ActionResult.success("click", "Clicked: $text")
                else ActionResult.error("click", "Failed to click: $text")
            }
            "type" -> {
                val label = step.parameters["label"] as? String ?: ""
                val text = step.parameters["text"] as? String ?: ""
                val result = accessibilityActions.typeText(label, text)
                if (result) ActionResult.success("type", "Typed: $text")
                else ActionResult.error("type", "Failed to type")
            }
            "read_screen" -> {
                val result = accessibilityActions.readScreen()
                ActionResult.success("read_screen", result)
            }
            "scroll_down" -> {
                val result = accessibilityActions.scrollDown()
                if (result) ActionResult.success("scroll", "Scrolled down")
                else ActionResult.error("scroll", "Scroll failed")
            }
            "scroll_up" -> {
                val result = accessibilityActions.scrollUp()
                if (result) ActionResult.success("scroll", "Scrolled up")
                else ActionResult.error("scroll", "Scroll failed")
            }
            "back" -> {
                val result = accessibilityActions.pressBack()
                if (result) ActionResult.success("back", "Back pressed")
                else ActionResult.error("back", "Back press failed")
            }
            "home" -> {
                val result = accessibilityActions.pressHome()
                if (result) ActionResult.success("home", "Home pressed")
                else ActionResult.error("home", "Home press failed")
            }
            "wait" -> {
                val ms = (step.parameters["duration"] as? Number)?.toLong() ?: 2000L
                kotlinx.coroutines.delay(ms)
                ActionResult.success("wait", "Waited ${ms}ms")
            }
            "delay" -> {
                val ms = (step.parameters["milliseconds"] as? Number)?.toLong() ?: 1000L
                kotlinx.coroutines.delay(ms)
                ActionResult.success("delay", "Delayed ${ms}ms")
            }
            else -> {
                val params = step.parameters.toMutableMap()
                params["action"] = step.action
                toolRouter.executeTool(step.action, params)
            }
        }
    }

    private fun verify(result: ActionResult, description: String): ActionResult {
        return if (result.success) result else ActionResult.error(result.action, "$description failed", result.error ?: result.message)
    }
}
