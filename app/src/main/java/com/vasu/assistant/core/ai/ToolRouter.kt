package com.vasu.assistant.core.ai

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolRouter @Inject constructor() {

    fun getAvailableTools(): List<ToolDefinition> {
        return listOf(
            // SYSTEM & DEVICE - Maya same
            ToolDefinition("turn_on_torch", "Turn on the device flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("turn_off_torch", "Turn off the device flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("torch_on", "Turn on flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("torch_off", "Turn off flashlight", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("volume_up", "Increase device media volume by 15%", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("volume_down", "Decrease device media volume by 15%", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("set_volume", "Set device volume to a specific percentage (0-100)", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("mute", "Mute device", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("bluetooth_on", "Enable Bluetooth", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("bluetooth_off", "Disable Bluetooth", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("battery_info", "Check battery level and charging status", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("device_info", "Get hardware, OS version, RAM, and display specs", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("storage_info", "Check storage usage and available space", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            // COMMUNICATION - Maya same
            ToolDefinition("open_whatsapp", "Open WhatsApp", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("make_call", "Make a phone call", riskLevel = com.vasu.assistant.core.security.RiskLevel.HIGH),
            ToolDefinition("send_message", "Send SMS or WhatsApp message", riskLevel = com.vasu.assistant.core.security.RiskLevel.HIGH),
            ToolDefinition("lookup_contact", "Lookup contact by name", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            // MEDIA - Maya same
            ToolDefinition("media_play_pause", "Play or pause media", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("media_next", "Play next track", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("open_camera", "Open camera", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("take_photo", "Take a photo", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            // SYSTEM TOOLS - Maya same
            ToolDefinition("search_web", "Search the web", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("read_screen", "Read current screen content", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("click", "Click on screen element", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("type_text", "Type text into input field", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("press_back", "Press back button", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("press_home", "Press home button", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("scroll_down", "Scroll down", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("scroll_up", "Scroll up", riskLevel = com.vasu.assistant.core.security.RiskLevel.LOW),
            ToolDefinition("open_app", "Open an app by name", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("ocr_extract", "Extract text from screen using OCR", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            // FILES - Maya same
            ToolDefinition("browse_files", "Browse files in a folder", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("search_files", "Search for a file", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("delete_file", "Delete a file", riskLevel = com.vasu.assistant.core.security.RiskLevel.HIGH),
            // ALARMS & MISSIONS - Maya same
            ToolDefinition("create_alarm", "Create an alarm", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("run_mission", "Run a saved mission/automation", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM),
            ToolDefinition("read_notifications", "Read notifications", riskLevel = com.vasu.assistant.core.security.RiskLevel.MEDIUM)
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
