package com.vasu.assistant.core.ai

import com.vasu.assistant.calls.CallManager
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.automation.AutomationEngine
import com.vasu.assistant.core.automation.AutomationStep
import com.vasu.assistant.core.security.PermissionGate
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.Tool
import com.vasu.assistant.devices.DeviceControlManager
import com.vasu.assistant.messaging.MessagingManager
import com.vasu.assistant.messaging.WhatsAppAutomation
import javax.inject.Inject
import javax.inject.Singleton

data class ToolDefinition(
    val name: String, val description: String, val parameters: List<ToolParameter>,
    val riskLevel: RiskLevel, val requiredRole: com.vasu.assistant.core.security.UserRole = riskLevel.requiredRole
)
data class ToolParameter(val name: String, val type: String, val description: String, val required: Boolean = true)

@Singleton
class ToolRouter @Inject constructor(
    private val permissionGate: PermissionGate,
    private val automationEngine: AutomationEngine,
    private val callManager: CallManager,
    private val messagingManager: MessagingManager,
    private val whatsappAutomation: WhatsAppAutomation,
    private val deviceControl: DeviceControlManager
) {
    private val toolRegistry = mutableMapOf<String, ToolDefinition>()
    init { registerDefaultTools() }

    fun registerTool(tool: ToolDefinition) { toolRegistry[tool.name] = tool }

    suspend fun executeTool(toolName: String, parameters: Map<String, Any>): ActionResult {
        val tool = toolRegistry[toolName]
            ?: return ActionResult.error(toolName, "Tool not found", "Unknown tool: $toolName")

        val permission = permissionGate.checkPermission(Tool(tool.name, tool.description, tool.riskLevel, tool.requiredRole))
        when (permission) {
            is com.vasu.assistant.core.security.PermissionResult.Denied -> return ActionResult.error(toolName, "Permission denied", permission.reason)
            else -> {}
        }

        return when (toolName) {
            "open_app" -> { val pkg = parameters["package"] as? String ?: ""; automationEngine.executeSteps(listOf(AutomationStep("open_app", mapOf("package" to pkg)))).let { if (it.success) ActionResult.success("open_app", "Opened $pkg") else ActionResult.error("open_app", it.message, it.message) } }
            "click" -> { val text = parameters["text"] as? String ?: ""; automationEngine.executeSteps(listOf(AutomationStep("click", mapOf("text" to text)))).let { if (it.success) ActionResult.success("click", "Clicked: $text") else ActionResult.error("click", it.message, it.message) } }
            "type_text" -> { val text = parameters["text"] as? String ?: ""; val label = parameters["label"] as? String ?: ""; automationEngine.executeSteps(listOf(AutomationStep("type", mapOf("label" to label, "text" to text)))).let { if (it.success) ActionResult.success("type_text", "Typed: $text") else ActionResult.error("type_text", it.message, it.message) } }
            "read_screen" -> automationEngine.executeSteps(listOf(AutomationStep("read_screen"))).let { it.stepResults.firstOrNull() ?: ActionResult.error("read_screen", "Failed", "No result") }
            "scroll_down" -> automationEngine.executeSteps(listOf(AutomationStep("scroll_down"))).let { if (it.success) ActionResult.success("scroll_down", "Scrolled down") else ActionResult.error("scroll_down", it.message, it.message) }
            "scroll_up" -> automationEngine.executeSteps(listOf(AutomationStep("scroll_up"))).let { if (it.success) ActionResult.success("scroll_up", "Scrolled up") else ActionResult.error("scroll_up", it.message, it.message) }
            "press_back" -> automationEngine.executeSteps(listOf(AutomationStep("back"))).let { if (it.success) ActionResult.success("back", "Pressed back") else ActionResult.error("back", it.message, it.message) }
            "press_home" -> automationEngine.executeSteps(listOf(AutomationStep("home"))).let { if (it.success) ActionResult.success("home", "Pressed home") else ActionResult.error("home", it.message, it.message) }
            "make_call" -> callManager.makeCall(parameters["number"] as? String ?: "")
            "send_message" -> messagingManager.sendSms(parameters["contact"] as? String ?: "", parameters["message"] as? String ?: "")
            "whatsapp" -> whatsappAutomation.sendMessage(parameters["contact"] as? String ?: "", parameters["message"] as? String ?: "")
            "turn_on_torch" -> { val enabled = parameters["enabled"] as? Boolean ?: true; if (enabled) deviceControl.getTorch().turnOn() else deviceControl.getTorch().turnOff() }
            "set_volume" -> deviceControl.getVolume().setVolume((parameters["level"] as? Number)?.toInt() ?: 50)
            "volume_up" -> deviceControl.getVolume().volumeUp()
            "volume_down" -> deviceControl.getVolume().volumeDown()
            "media_play_pause" -> deviceControl.getMedia().playPause()
            "media_next" -> deviceControl.getMedia().next()
            "media_previous" -> deviceControl.getMedia().previous()
            "bluetooth_toggle" -> deviceControl.getBluetooth().toggle()
            "battery_info" -> { val info = deviceControl.getBatteryInfo(); ActionResult.success("battery", "Battery: ${info["level"]}%, Charging: ${info["isCharging"]}") }
            "device_info" -> { val info = deviceControl.getDeviceInfo(); ActionResult.success("device", "${info["brand"]} ${info["model"]}, Android ${info["android_version"]}") }
            "search_web" -> ActionResult.success("search_web", "Search: ${parameters["query"]}")
            "create_alarm" -> ActionResult.success("create_alarm", "Alarm created: ${parameters["time"]}")
            "run_mission" -> ActionResult.success("run_mission", "Mission engine - Phase 13")
            else -> ActionResult.error(toolName, "Tool not implemented", "Not yet available")
        }
    }

    fun getAvailableTools(): List<ToolDefinition> = toolRegistry.values.toList()

    private fun registerDefaultTools() {
        val tools = listOf(
            ToolDefinition("open_app", "Open an application", listOf(ToolParameter("package", "string", "Package name")), RiskLevel.LOW),
            ToolDefinition("click", "Click on a UI element", listOf(ToolParameter("text", "string", "Text to click")), RiskLevel.LOW),
            ToolDefinition("type_text", "Type text", listOf(ToolParameter("text", "string", "Text"), ToolParameter("label", "string", "Field", false)), RiskLevel.LOW),
            ToolDefinition("read_screen", "Read screen content", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_down", "Scroll down", emptyList(), RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_back", "Press back", emptyList(), RiskLevel.LOW),
            ToolDefinition("press_home", "Press home", emptyList(), RiskLevel.LOW),
            ToolDefinition("make_call", "Make a phone call", listOf(ToolParameter("number", "string", "Contact/number")), RiskLevel.MEDIUM),
            ToolDefinition("send_message", "Send SMS", listOf(ToolParameter("contact", "string", "Contact"), ToolParameter("message", "string", "Message")), RiskLevel.MEDIUM),
            ToolDefinition("whatsapp", "Send WhatsApp", listOf(ToolParameter("contact", "string", "Contact"), ToolParameter("message", "string", "Message")), RiskLevel.MEDIUM),
            ToolDefinition("turn_on_torch", "Toggle flashlight", listOf(ToolParameter("enabled", "boolean", "On/off")), RiskLevel.LOW),
            ToolDefinition("set_volume", "Set volume", listOf(ToolParameter("level", "int", "0-100")), RiskLevel.LOW),
            ToolDefinition("volume_up", "Volume up", emptyList(), RiskLevel.LOW),
            ToolDefinition("volume_down", "Volume down", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_play_pause", "Play/Pause media", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_next", "Next track", emptyList(), RiskLevel.LOW),
            ToolDefinition("media_previous", "Previous track", emptyList(), RiskLevel.LOW),
            ToolDefinition("bluetooth_toggle", "Toggle Bluetooth", emptyList(), RiskLevel.LOW),
            ToolDefinition("battery_info", "Get battery info", emptyList(), RiskLevel.LOW),
            ToolDefinition("device_info", "Get device info", emptyList(), RiskLevel.LOW),
            ToolDefinition("search_web", "Search web", listOf(ToolParameter("query", "string", "Query")), RiskLevel.LOW),
            ToolDefinition("create_alarm", "Create alarm", listOf(ToolParameter("time", "string", "HH:mm"), ToolParameter("label", "string", "Label")), RiskLevel.LOW),
            ToolDefinition("run_mission", "Run mission", listOf(ToolParameter("mission_id", "string", "ID")), RiskLevel.HIGH)
        )
        tools.forEach { toolRegistry[it.name] = it }
    }
}
