package com.vasu.ai.memory

data class VasuMemoryEntry(
    val key: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
