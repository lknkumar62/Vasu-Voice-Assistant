package com.vasu.assistant.core.ai

import com.vasu.assistant.calls.CallManager
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.AutomationEngine
import com.vasu.assistant.core.automation.AutomationStep
import com.vasu.assistant.core.security.PermissionGate
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.Tool
import com.vasu.assistant.messaging.MessagingManager
import com.vasu.assistant.messaging.WhatsAppAutomation
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class ToolRouter @Inject constructor(
    private val permissionGate: PermissionGate,
    private val automationEngine: AutomationEngine,
    private val callManager: CallManager,
    private val messagingManager: MessagingManager,
    private val whatsappAutomation: WhatsAppAutomation
) {
    private val toolRegistry = mutableMapOf<String, ToolDefinition>()

    init { registerDefaultTools() }

    fun registerTool(tool: ToolDefinition) { toolRegistry[tool.name] = tool }

    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ActionResult {
        val tool = toolRegistry[toolName]
            ?: return ActionResult.error(toolName, "Tool not found", "Unknown tool: $toolName")

        val toolForCheck = Tool(tool.name, tool.description, tool.riskLevel, tool.requiredRole)
        val permission = permissionGate.checkPermission(toolForCheck)

        when (permission) {
            is com.vasu.assistant.core.security.PermissionResult.Denied -> {
                return ActionResult.error(toolName, "Permission denied", permission.reason)
            }
            else -> {}
        }

        return when (toolName) {
            "open_app" -> executeOpenApp(parameters)
            "click" -> executeClick(parameters)
            "type_text" -> executeTypeText(parameters)
            "read_screen" -> executeReadScreen()
            "scroll_down" -> executeScroll("down")
            "scroll_up" -> executeScroll("up")
            "press_back" -> executePressBack()
            "press_home" -> executePressHome()
            "make_call" -> {
                val contact = parameters["number"] as? String ?: ""
                callManager.makeCall(contact)
            }
            "send_message" -> {
                val contact = parameters["contact"] as? String ?: ""
                val message = parameters["message"] as? String ?: ""
                messagingManager.sendSms(contact, message)
            }
            "whatsapp" -> {
                val contact = parameters["contact"] as? String ?: ""
                val message = parameters["message"] as? String ?: ""
                whatsappAutomation.sendMessage(contact, message)
            }
            "search_web" -> ActionResult.success("search_web", "Search: ${parameters["query"]}")
            "set_volume" -> ActionResult.success("set_volume", "Volume set")
            "turn_on_torch" -> ActionResult.success("turn_on_torch", "Torch toggled")
            "create_alarm" -> ActionResult.success("create_alarm", "Alarm created")
            "run_mission" -> ActionResult.success("run_mission", "Mission engine - Phase 13")
            else -> ActionResult.error(toolName, "Tool not implemented", "Not yet available")
        }
    }

    fun getAvailableTools(): List<ToolDefinition> = toolRegistry.values.toList()

    private fun registerDefaultTools() {
        val tools = listOf(
            ToolDefinition("open_app", "Open an application", listOf(ToolParameter("package", "string", "Package name")), RiskLevel.LOW),
            ToolDefinition("click", "Click on a UI element", listOf(ToolParameter("text", "string", "Text to click")), RiskLevel.LOW),
            ToolDefinition("type_text", "Type text into a field", listOf(ToolParameter("text", "string", "Text to type"), ToolParameter("label", "string", "Field label", false)), RiskLevel.LOW),
            ToolDefinition("read_screen", "Read current screen content", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_down", "Scroll down", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_back", "Press back button", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_home", "Press home button", emptyList(), RiskLevel.LOW),
            ToolDefinition("make_call", "Make a phone call", listOf(ToolParameter("number", "string", "Phone number or contact")), RiskLevel.MEDIUM),
            ToolDefinition("send_message", "Send SMS message", listOf(ToolParameter("contact", "string", "Contact name"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("whatsapp", "Send WhatsApp message", listOf(ToolParameter("contact", "string", "Contact name"), ToolParameter("message", "string", "Message text")), RiskLevel.MEDIUM),
            ToolDefinition("search_web", "Search the web", listOf(ToolParameter("query", "string", "Search query")), RiskLevel.LOW),
            ToolDefinition("set_volume", "Set device volume", listOf(ToolParameter("level", "int", "Volume level 0-100")), RiskLevel.LOW),
            ToolDefinition("turn_on_torch", "Toggle flashlight", listOf(ToolParameter("enabled", "boolean", "On or off")), RiskLevel.LOW),
            ToolDefinition("create_alarm", "Create an alarm", listOf(ToolParameter("time", "string", "Time HH:mm"), ToolParameter("label", "string", "Alarm label")), RiskLevel.LOW),
            ToolDefinition("run_mission", "Run automation mission", listOf(ToolParameter("mission_id", "string", "Mission ID")), RiskLevel.HIGH)
        )
        tools.forEach { toolRegistry[it.name] = it }
    }

    private suspend fun executeOpenApp(params: Map<String, Any>): ActionResult {
        val pkg = params["package"] as? String ?: return ActionResult.error("open_app", "Missing package", "No package name")
        val steps = listOf(AutomationStep("open_app", mapOf("package" to pkg)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("open_app", "Opened $pkg") else ActionResult.error("open_app", result.message, result.message)
    }

    private suspend fun executeClick(params: Map<String, Any>): ActionResult {
        val text = params["text"] as? String ?: return ActionResult.error("click", "Missing text", "No text")
        val steps = listOf(AutomationStep("click", mapOf("text" to text)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("click", "Clicked: $text") else ActionResult.error("click", result.message, result.message)
    }

    private suspend fun executeTypeText(params: Map<String, Any>): ActionResult {
        val text = params["text"] as? String ?: return ActionResult.error("type_text", "Missing text", "No text")
        val label = params["label"] as? String ?: ""
        val steps = listOf(AutomationStep("type", mapOf("label" to label, "text" to text)))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("type_text", "Typed: $text") else ActionResult.error("type_text", result.message, result.message)
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
        return if (result.success) ActionResult.success(action, "Scrolled $direction") else ActionResult.error(action, result.message, result.message)
    }

    private suspend fun executePressBack(): ActionResult {
        val steps = listOf(AutomationStep("back"))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("back", "Pressed back") else ActionResult.error("back", result.message, result.message)
    }

    private suspend fun executePressHome(): ActionResult {
        val steps = listOf(AutomationStep("home"))
        val result = automationEngine.executeSteps(steps)
        return if (result.success) ActionResult.success("home", "Pressed home") else ActionResult.error("home", result.message, result.message)
    }
}
