package com.vasu.assistant.ui.tools

import androidx.lifecycle.ViewModel
import com.vasu.assistant.core.ai.ToolRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class ToolsUiState(
    val tools: List<Map<String, Any>> = emptyList()
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val toolRouter: ToolRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState

    init {
        _uiState.value = ToolsUiState(
            tools = toolRouter.getAvailableTools().map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "riskLevel" to tool.riskLevel.name
                )
            }
        )
    }
}
