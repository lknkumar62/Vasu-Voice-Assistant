package com.vasu.assistant.ui.missions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.automation.Mission
import com.vasu.assistant.core.automation.MissionEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MissionsViewModel @Inject constructor(
    private val missionEngine: MissionEngine
) : ViewModel() {
    val missions: StateFlow<List<Mission>> = missionEngine.missions

    fun executeMission(mission: Mission) {
        viewModelScope.launch {
            missionEngine.executeMission(mission)
        }
    }
}
