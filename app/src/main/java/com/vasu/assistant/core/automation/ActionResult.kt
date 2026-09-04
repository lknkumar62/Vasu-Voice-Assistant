package com.vasu.assistant.core.automation

/**
 * Result of an automation action
 */
data class ActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val error: String? = null,
    val data: Map<String, Any>? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun success(action: String, message: String, data: Map<String, Any>? = null) = ActionResult(
            success = true,
            action = action,
            message = message,
            data = data
        )

        fun error(action: String, message: String, error: String, data: Map<String, Any>? = null) = ActionResult(
            success = false,
            action = action,
            message = message,
            error = error,
            data = data
        )
    }
}

/**
 * Automation step
 */
data class AutomationStep(
    val action: String,
    val parameters: Map<String, Any> = emptyMap(),
    val description: String = "",
    val timeout: Long = 5000L,
    val retryCount: Int = 2
)

/**
 * Automation result
 */
data class AutomationResult(
    val success: Boolean,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val message: String,
    val stepResults: List<ActionResult> = emptyList()
)
