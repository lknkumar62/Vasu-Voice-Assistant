package com.vasu.ai.core

data class VasuCommandProcessingResult(
    val handled: Boolean,
    val onlineRequired: Boolean,
    val actionCount: Int,
    val response: String?,
    val reason: String
)
