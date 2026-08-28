package com.vasu.assistant.core.ai

import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.AutomationEngine
import com.vasu.assistant.core.automation.AutomationStep
import com.vasu.assistant.core.security.PermissionGate
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.Tool
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tool registry entry
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val riskLevel: RiskLevel,
    val requiredRole: com.vasu.assistant.core.security.UserRole = riskLevel.requiredRole
)

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)

/**
 * ToolRouter - Routes AI commands to appropriate tools.
 *
 * Maintains a registry of available tools and routes
 * AI-selected tools through permission checking.
 */
@Singleton
class ToolRouter @Inject constructor(
    private val permissionGate: PermissionGate,
    private val automationEngine: AutomationEngine
) {
    private val toolRegistry = mutableMapOf<String, ToolDefinition>()

    init {
        registerDefaultTools()
    }

    /**
     * Register a new tool
     */
    fun registerTool(tool: ToolDefinition) {
        toolRegistry[tool.name] = tool
    }

    /**
     * Execute a tool by name with parameters
     */
    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ActionResult {
        val tool = toolRegistry[toolName]
            ?: return ActionResult.error(toolName, "Tool not found", "Unknown tool: $toolName")

        // Check permission
        val toolForCheck = Tool(
            name = tool.name,
            description = tool.description,
            riskLevel = tool.riskLevel,
            requiredRole = tool.requiredRole
        )
        val permission = permissionGate.checkPermission(toolForCheck)

        when (permission) {
            is com.vasu.assistant.core.security.PermissionResult.Denied -> {
                return ActionResult.error(toolName, "Permission denied", permission.reason)
            }
            is com.vasu.assistant.core.security.PermissionResult.RequiresConfirmation -> {
                // In Phase 6, auto-confirm for demo
                // Phase 15: Will add user confirmation UI
            }
            is com.vasu.assistant.core.security.PermissionResult.Granted -> {}
        }

        // Execute tool
        return when (toolName) {
            "open_app" -> executeOpenApp(parameters)
            "click" -> executeClick(parameters)
            "type_text" -> executeTypeText(parameters)
            "read_screen" -> executeReadScreen()
            "scroll_down" -> executeScroll("down")
            "scroll_up" -> executeScroll("up")
            "press_back" -> executePressBack()
            "press_home" -> executePressHome()
            "take_photo" -> ActionResult.success("take_photo", "Camera feature - Phase 10")
            "send_message" -> executeSendMessage(parameters)
            "make_call" -> executeMakeCall(parameters)
            "search_web" -> executeSearchWeb(parameters)
            "set_volume" -> executeSetVolume(parameters)
            "turn_on_torch" -> executeTorch(parameters)
            "create_alarm" -> executeCreateAlarm(parameters)
            "run_mission" -> ActionResult.success("run_mission", "Mission engine - Phase 13")
            else -> ActionResult.error(toolName, "Tool not implemented", "Not yet available")
        }
    }

    /**
     * Get all available tools
     */
    fun getAvailableTools(): List<ToolDefinition> = toolRegistry.values.toList()

    /**
     * Get tools by risk level
     */
    fun getToolsByRiskLevel(riskLevel: RiskLevel): List<ToolDefinition> {
        return toolRegistry.values.filter { it.riskLevel == riskLevel }
    }

    private fun registerDefaultTools() {
        val tools = listOf(
            ToolDefinition("open_app", "Open an application", listOf(
                ToolParameter("package", "string", "Package name")
            ), RiskLevel.LOW),

            ToolDefinition("click", "Click on a UI element by text", listOf(
                ToolParameter("text", "string", "Text to click")
            ), RiskLevel.LOW),

            ToolDefinition("type_text", "Type text into a field", listOf(
                ToolParameter("label", "string", "Field label"),
                ToolParameter("text", "string", "Text to type")
            ), RiskLevel.LOW),

            ToolDefinition("read_screen", "Read current screen content", emptyList(), RiskLevel.LOW),

            ToolDefinition("scroll_down", "Scroll down", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up", emptyList(), RiskLevel.LOW),

            ToolDefinition("press_back", "Press back button", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_home", "Press home button", emptyList(), RiskLevel.LOW),

            ToolDefinition("take_photo", "Take a photo", emptyList(), RiskLevel.MEDIUM),
            ToolDefinition("send_message", "Send a message", listOf(
                ToolParameter("contact", "string", "Contact name"),
                ToolParameter("message", "string", "Message text")
            ), RiskLevel.MEDIUM),

            ToolDefinition("make_call", "Make a phone call", listOf(
                ToolParameter("number", "string", "Phone number or contact")
            ), RiskLevel.MEDIUM),

            ToolDefinition("search_web", "Search the web", listOf(
                ToolParameter("query", "string", "Search query")
            ), RiskLevel.LOW),

            ToolDefinition("set_volume", "Set device volume", listOf(
                ToolParameter("level", "int", "Volume level 0-100")
            ), RiskLevel.LOW),

            ToolDefinition("turn_on_torch", "Toggle flashlight", listOf(
                ToolParameter("enabled", "boolean", "Turn on or off")
            ), RiskLevel.LOW),

            ToolDefinition("create_alarm", "Create an alarm", listOf(
                ToolParameter("time", "string", "Alarm time (HH:mm)"),
                ToolParameter("label", "string", "Alarm label")
            ), RiskLevel.LOW),

            ToolDefinition("run_mission", "Run an automation mission", listOf(
                ToolParameter("mission_id", "string", "Mission ID")
            ), RiskLevel.HIGH)
        )

        tools.forEach { toolRegistry[it.name] = it }
    }

    // Tool implementations

    private suspend fun executeOpenApp(params: Map<String, Any>): ActionResult {
        val pkg = params["package"] as? String ?: return ActionResult.error("open_app", "Missing package", "No package name")
        val steps = listOf(AutomationStep("open_app", mapOf("package" to pkg)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("open_app", "Opened $pkg")
        else ActionResult.error("open_app", result.message, result.message)
    }

    private suspend fun executeClick(params: Map<String, Any>): ActionResult {
        val text = params["text"] as? String ?: return ActionResult.error("click", "Missing text", "No text")
        val steps = listOf(AutomationStep("click", mapOf("text" to text)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("click", "Clicked: $text")
        else ActionResult.error("click", result.message, result.message)
    }

    private suspend fun executeTypeText(params: Map<String, Any>): ActionResult {
        val label = params["label"] as? String ?: ""
        val text = params["text"] as? String ?: return ActionResult.error("type_text", "Missing text", "No text")
        val steps = listOf(AutomationStep("type", mapOf("label" to label, "text" to text)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("type_text", "Typed: $text")
        else ActionResult.error("type_text", result.message, result.message)
    }

    private suspend fun executeReadScreen(): ActionResult {
        val steps = listOf(AutomationStep("read_screen"))
        val result = automationEngine.executeSteps(steps)
        return result.stepResults.firstOrNull() ?: ActionResult.error("read_screen", "Failed", "No result")
    }

    private suspend fun executeScroll(direction: String): ActionResult {
        val action = if (direction == "down") "scroll_down" else "scroll_up"
        val steps = listOf(AutomationStep(action))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success(action, "Scrolled $direction")
        else ActionResult.error(action, result.message, result.message)
    }

    private suspend fun executePressBack(): ActionResult {
        val steps = listOf(AutomationStep("back"))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("back", "Pressed back")
        else ActionResult.error("back", result.message, result.message)
    }

    private suspend fun executePressHome(): ActionResult {
        val steps = listOf(AutomationStep("home"))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("home", "Pressed home")
        else ActionResult.error("home", result.message, result.message)
    }

    private fun executeSendMessage(params: Map<String, Any>): ActionResult {
        val contact = params["contact"] as? String ?: "Unknown"
        val message = params["message"] as? String ?: ""
        // Phase 8 will implement actual messaging
        return ActionResult.success("send_message", "Message to $contact: \"$message\" (Phase 8)")
    }

    private fun executeMakeCall(params: Map<String, Any>): ActionResult {
        val number = params["number"] as? String ?: "Unknown"
        // Phase 8 will implement actual calling
        return ActionResult.success("make_call", "Call to $number (Phase 8)")
    }

    private fun executeSearchWeb(params: Map<String, Any>): ActionResult {
        val query = params["query"] as? String ?: ""
        return ActionResult.success("search_web", "Search: $query (Phase 6)")
    }

    private fun executeSetVolume(params: Map<String, Any>): ActionResult {
        val level = (params["level"] as? Number)?.toInt() ?: 50
        // Phase 9 will implement actual volume control
        return ActionResult.success("set_volume", "Volume set to $level% (Phase 9)")
    }

    private fun executeTorch(params: Map<String, Any>): ActionResult {
        val enabled = params["enabled"] as? Boolean ?: true
        // Phase 9 will implement actual torch control
        return ActionResult.success("turn_on_torch", "Torch ${if (enabled) "on" else "off"} (Phase 9)")
    }

    private fun executeCreateAlarm(params: Map<String, Any>): ActionResult {
        val time = params["time"] as? String ?: "08:00"
        val label = params["label"] as? String ?: "Alarm"
        // Phase 9 will implement actual alarm
        return ActionResult.success("create_alarm", "Alarm at $time: $label (Phase 9)")
    }
}
