package com.vasu.assistant.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.ai.ToolDefinition
import com.vasu.assistant.core.ai.ToolRouter
import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.security.RiskLevel
import com.vasu.assistant.core.security.UserRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val tools: List<ToolDefinition> = emptyList(),
    val filteredTools: List<ToolDefinition> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val isTesting: Boolean = false,
    val testResult: ActionResult? = null,
    val totalCount: Int = 0
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val toolRouter: ToolRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    init {
        loadTools()
    }

    fun loadTools() {
        val allTools = toolRouter.getAvailableTools()
        _uiState.value = _uiState.value.copy(
            tools = allTools,
            filteredTools = filterTools(allTools, _uiState.value.selectedCategory, _uiState.value.searchQuery),
            totalCount = allTools.size
        )
    }

    fun selectCategory(category: String) {
        val filtered = filterTools(_uiState.value.tools, category, _uiState.value.searchQuery)
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            filteredTools = filtered
        )
    }

    fun setSearchQuery(query: String) {
        val filtered = filterTools(_uiState.value.tools, _uiState.value.selectedCategory, query)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredTools = filtered
        )
    }

    fun executeTest(toolName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTesting = true, testResult = null)
            val defaultParams = getSafeDefaultParams(toolName)
            val result = toolRouter.executeTool(toolName, defaultParams)
            _uiState.value = _uiState.value.copy(isTesting = false, testResult = result)
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(testResult = null)
    }

    private fun getSafeDefaultParams(toolName: String): Map<String, Any> {
        return when (toolName) {
            "torch" -> mapOf("state" to "toggle")
            "set_volume" -> mapOf("level" to 50)
            "set_brightness" -> mapOf("level" to 50)
            "search_files" -> mapOf("query" to "pdf")
            "browse_files" -> mapOf("path" to "/sdcard/Download")
            "search_web" -> mapOf("query" to "India weather")
            "read_screen" -> mapOf("action" to "read_screen")
            "smart_mode" -> mapOf("mode" to "NORMAL")
            "create_alarm" -> mapOf("time" to "07:00")
            "set_timer" -> mapOf("seconds" to 10)
            "calculate" -> mapOf("expression" to "25 * 4")
            "currency_convert" -> mapOf("amount" to 100.0, "from" to "USD", "to" to "INR")
            "unit_convert" -> mapOf("value" to 10.0, "from" to "km", "to" to "miles")
            "weather" -> mapOf("city" to "Delhi")
            else -> emptyMap()
        }
    }

    private fun filterTools(tools: List<ToolDefinition>, category: String, query: String): List<ToolDefinition> {
        var result = tools
        if (category != "ALL") {
            result = result.filter { getCategoryForTool(it.name) == category }
        }
        if (query.isNotBlank()) {
            result = result.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true)
            }
        }
        return result
    }

    companion object {
        val CATEGORIES = listOf(
            "ALL" to "All",
            "DEVICE" to "Device & System",
            "FILES" to "Files & Storage",
            "VISION" to "Vision & Media",
            "ACCESSIBILITY" to "Accessibility",
            "COMMUNICATION" to "Communication",
            "LOCATION" to "Location & Maps",
            "SMART_MODE" to "Smart Modes",
            "UTILITIES" to "Utilities"
        )

        fun getCategoryForTool(name: String): String {
            return when {
                name in listOf("torch", "set_volume", "get_battery", "get_device_info", "open_settings", "set_brightness", "toggle_wifi", "set_ringer_mode", "list_apps", "get_time", "create_alarm", "set_timer") -> "DEVICE"
                name in listOf("browse_files", "search_files", "read_file", "storage_info", "rename_file", "copy_file", "move_file", "delete_file") -> "FILES"
                name in listOf("take_photo", "record_video", "stop_recording", "media_control") -> "VISION"
                name in listOf("read_screen", "click_element", "type_text", "scroll", "go_back", "go_home", "read_notifications", "dismiss_notification") -> "ACCESSIBILITY"
                name in listOf("make_call", "send_sms", "send_whatsapp") -> "COMMUNICATION"
                name in listOf("get_current_location", "save_parking", "get_parking_location", "find_nearby_places", "get_traffic_info") -> "LOCATION"
                name in listOf("smart_mode") -> "SMART_MODE"
                name in listOf("search_web", "calculate", "currency_convert", "unit_convert", "weather") -> "UTILITIES"
                else -> "DEVICE"
            }
        }
    }
}
