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
                if (result.success) result else ActionResult.error("open_app", "Open app failed: $pkg", result.error ?: result.message)
            }
            "click" -> {
                val text = step.parameters["text"] as? String ?: ""
                accessibilityActions.clickByText(text)
            }
            "type" -> {
                val label = step.parameters["label"] as? String ?: ""
                val text = step.parameters["text"] as? String ?: ""
                accessibilityActions.typeTextByLabel(label, text)
            }
            "read_screen" -> {
                accessibilityActions.readScreen()
            }
            "scroll_down" -> {
                accessibilityActions.scrollDown()
            }
            "scroll_up" -> {
                accessibilityActions.scrollUp()
            }
            "back" -> {
                accessibilityActions.pressBack()
            }
            "home" -> {
                accessibilityActions.pressHome()
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
}
