package com.vasu.assistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.memory.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryUiState(
    val memories: List<Map<String, Any>> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState

    init { loadMemories() }

    fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val memories = memoryRepository.getAllMemory().map { mem ->
                mapOf("key" to mem.key, "value" to mem.value, "confidence" to mem.confidence)
            }
            _uiState.value = MemoryUiState(memories = memories, isLoading = false)
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            memoryRepository.forget(key)
            loadMemories()
        }
    }
}
