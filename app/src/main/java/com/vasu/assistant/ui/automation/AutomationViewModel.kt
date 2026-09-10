package com.vasu.assistant.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.automation.MacroEngine
import com.vasu.assistant.core.automation.MissionEngine
import com.vasu.assistant.core.automation.MissionStep
import com.vasu.assistant.maps.SmartModeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AutomationUiState(
    val smartModes: List<String> = listOf("NORMAL", "DRIVING", "SLEEP", "WORK", "GAMING", "CUSTOM"),
    val activeMode: String = "NORMAL",
    val missions: List<Map<String, Any>> = emptyList(),
    val message: String = ""
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val missionEngine: MissionEngine,
    private val macroEngine: MacroEngine,
    private val smartModeManager: SmartModeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val modeResult = smartModeManager.getCurrentMode()
            val mode = (modeResult.data?.get("mode") as? String) ?: "NORMAL"
            _uiState.value = _uiState.value.copy(activeMode = mode)
        }
    }

    fun setSmartMode(modeName: String) {
        viewModelScope.launch {
            val mode = try { SmartModeManager.SmartMode.valueOf(modeName) } catch (_: Exception) { SmartModeManager.SmartMode.NORMAL }
            smartModeManager.setMode(mode)
            refresh()
        }
    }

    fun createMission(name: String, actionNames: List<String>) {
        val steps = actionNames.map { MissionStep(action = it) }
        missionEngine.createMission(name, steps)
    }

    fun runMission(missionId: String) {
        viewModelScope.launch {
            missionEngine.executeMission(missionId)
            refresh()
        }
    }

    fun createMacro(name: String, trigger: String, actions: List<String>) {
        val steps = actions.map { MissionStep(action = it) }
        macroEngine.createMacro(name, trigger, steps)
    }
}
