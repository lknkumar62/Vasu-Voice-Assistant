package com.vasu.assistant.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.automation.MacroEngine
import com.vasu.assistant.core.automation.MissionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MissionsUiState(
    val activeMission: String? = null,
    val macros: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = false,
    val message: String = ""
)

@HiltViewModel
class MissionsViewModel @Inject constructor(
    private val missionEngine: MissionEngine,
    private val macroEngine: MacroEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissionsUiState())
    val uiState: StateFlow<MissionsUiState> = _uiState

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val macros = macroEngine.listMacros()
            val macroList = (macros.data?.get("macros") as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
            _uiState.value = _uiState.value.copy(
                activeMission = missionEngine.getActiveMissionId(),
                macros = macroList
            )
        }
    }

    fun runMacro(macroId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = macroEngine.runMacro(macroId)
            _uiState.value = _uiState.value.copy(isLoading = false, message = result.message)
            refresh()
        }
    }

    fun pauseMission() {
        missionEngine.pauseMission()
        refresh()
    }

    fun cancelMission() {
        val id = missionEngine.getActiveMissionId() ?: return
        missionEngine.cancelMission(id)
        refresh()
    }
}
