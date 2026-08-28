package com.vasu.assistant.core.automation

import com.vasu.assistant.accessibility.VasuAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AutomationEngine - Main engine for executing automation tasks.
 *
 * Coordinates accessibility service to perform multi-step actions
 * like opening apps, navigating, clicking, typing, etc.
 */
@Singleton
class AutomationEngine @Inject constructor() {

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentTask = MutableStateFlow<String?>(null)
    val currentTask: StateFlow<String?> = _currentTask.asStateFlow()

    /**
     * Execute a list of automation steps
     */
    suspend fun executeSteps(steps: List<AutomationStep>): AutomationResult {
        val service = VasuAccessibilityService.instance.value
            ?: return AutomationResult(
                success = false,
                stepsCompleted = 0,
                totalSteps = steps.size,
                message = "Accessibility service not enabled"
            )

        _isRunning.value = true
        val results = mutableListOf<ActionResult>()

        for ((index, step) in steps.withIndex()) {
            _currentTask.value = step.description.ifBlank { step.action }

            val result = executeStep(service, step, step.retryCount)
            results.add(result)

            if (!result.success) {
                _isRunning.value = false
                _currentTask.value = null
                return AutomationResult(
                    success = false,
                    stepsCompleted = index,
                    totalSteps = steps.size,
                    message = "Failed at step ${index + 1}: ${result.message}",
                    stepResults = results
                )
            }
        }

        _isRunning.value = false
        _currentTask.value = null

        return AutomationResult(
            success = true,
            stepsCompleted = steps.size,
            totalSteps = steps.size,
            message = "All steps completed successfully",
            stepResults = results
        )
    }

    /**
     * Execute a single step
     */
    private suspend fun executeStep(
        service: VasuAccessibilityService,
        step: AutomationStep,
        retries: Int
    ): ActionResult {
        repeat(retries) { attempt ->
            val result = when (step.action) {
                "open_app" -> {
                    val packageName = step.parameters["package"] as? String ?: ""
                    service.openApp(packageName)
                }
                "click" -> {
                    val text = step.parameters["text"] as? String ?: ""
                    service.clickElement(text)
                }
                "type" -> {
                    val label = step.parameters["label"] as? String ?: ""
                    val text = step.parameters["text"] as? String ?: ""
                    service.typeText(label, text)
                }
                "scroll_down" -> service.scrollDown()
                "scroll_up" -> service.scrollUp()
                "back" -> service.pressBack()
                "home" -> service.pressHome()
                "read_screen" -> service.readScreen()
                "wait" -> {
                    val delay = step.parameters["delay"] as? Long ?: 1000L
                    kotlinx.coroutines.delay(delay)
                    ActionResult.success("wait", "Waited ${delay}ms")
                }
                else -> ActionResult.error(step.action, "Unknown action", "Action not supported")
            }

            if (result.success) return result

            if (attempt < retries - 1) {
                kotlinx.coroutines.delay(1000) // Wait before retry
            }
        }

        return ActionResult.error(step.action, "Failed after $retries retries", "Max retries exceeded")
    }

    /**
     * Stop current automation
     */
    fun stop() {
        _isRunning.value = false
        _currentTask.value = null
    }
}
