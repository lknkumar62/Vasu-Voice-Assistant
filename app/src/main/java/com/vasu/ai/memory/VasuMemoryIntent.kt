package com.vasu.ai.memory

sealed class VasuMemoryIntent {

    data class Remember(
        val key: String,
        val value: String
    ) : VasuMemoryIntent()

    data class Recall(
        val key: String
    ) : VasuMemoryIntent()

    data class Forget(
        val key: String
    ) : VasuMemoryIntent()

    data object None : VasuMemoryIntent()
}
