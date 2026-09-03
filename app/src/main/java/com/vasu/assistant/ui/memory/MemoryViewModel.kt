package com.vasu.assistant.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.memory.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryItem(
    val id: Long,
    val key: String,
    val value: String,
    val confidence: Float,
    val source: String,
    val updatedAt: Long
)

data class MemoryUiState(
    val memories: List<MemoryItem> = emptyList(),
    val filteredMemories: List<MemoryItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val totalCount: Int = 0
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val entities = memoryRepository.getAllMemory()
            val items = entities.map { entity ->
                MemoryItem(
                    id = entity.id,
                    key = entity.key,
                    value = entity.value,
                    confidence = entity.confidence,
                    source = entity.source,
                    updatedAt = entity.updatedAt
                )
            }
            val count = memoryRepository.getMemoryCount()
            val filtered = filterItems(items, _uiState.value.searchQuery)
            _uiState.value = _uiState.value.copy(
                memories = items,
                filteredMemories = filtered,
                totalCount = count,
                isLoading = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        val filtered = filterItems(_uiState.value.memories, query)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredMemories = filtered
        )
    }

    fun addMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            memoryRepository.remember(key.trim(), value.trim(), source = "user_manual")
            loadMemories()
        }
    }

    fun updateMemory(key: String, value: String) {
        if (key.isBlank() || value.isBlank()) return
        viewModelScope.launch {
            memoryRepository.remember(key.trim(), value.trim(), source = "user_edit")
            loadMemories()
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            memoryRepository.forget(key)
            loadMemories()
        }
    }

    fun clearAllMemory() {
        viewModelScope.launch {
            memoryRepository.clearAllMemory()
            loadMemories()
        }
    }

    private fun filterItems(items: List<MemoryItem>, query: String): List<MemoryItem> {
        if (query.isBlank()) return items
        return items.filter {
            it.key.contains(query, ignoreCase = true) ||
            it.value.contains(query, ignoreCase = true)
        }
    }
}
