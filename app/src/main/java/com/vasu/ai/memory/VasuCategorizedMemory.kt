package com.vasu.ai.memory

data class VasuCategorizedMemory(
    val key: String,
    val value: String,
    val category: VasuMemoryCategory,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
