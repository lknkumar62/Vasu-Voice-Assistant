package com.vasu.ai.memory

sealed class VasuMemoryWriteResult {
    data object Saved : VasuMemoryWriteResult()
    data object Rejected : VasuMemoryWriteResult()
}
