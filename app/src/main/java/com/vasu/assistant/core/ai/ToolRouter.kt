package com.vasu.assistant.core.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRouter @Inject constructor() {

    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            ToolDefinition("torch_on", "Turn on flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("torch_off", "Turn off flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("volume_up", "Increase volume", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("volume_down", "Decrease volume", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("mute", "Mute device", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("bluetooth_on", "Enable Bluetooth", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("bluetooth_off", "Disable Bluetooth", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("open_camera", "Open camera", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("battery_info", "Get battery information", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("send_message", "Send SMS or WhatsApp message", riskLevel = com.vasu.assistant.core.security.RiskLevel.HIGH),
            ToolDefinition("make_call", "Make a phone call", riskLevel = com.vasu.assistant.core.security.RiskLevel.HIGH),
            ToolDefinition("set_alarm", "Set an alarm", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM)
        )
    }

    fun findTool(name: String): ToolDefinition? = getAvailableTools().find { it.name == name }

    fun executeTool(name: String, params: Map<String, Any>): com.vasu.assistant.core.automation.ActionResult {
        Log.i(TAG, "Executing tool: $name with params: $params")
        return com.vasu.assistant.core.automation.ActionResult(
            success = true,
            action = name,
            message = "Tool '$name' executed"
        )
    }

    companion object {
        private const val TAG = "ToolRouter"
    }
}
