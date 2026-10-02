package com.vasu.assistant.core.ai

import kotlinx.coroutines.flow.Flow

enum class AIProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    CLAUDE("Anthropic Claude"),
    LOCAL("Local (Offline Only)")
}

/** Failure raised by streaming generation; mirrors the AiErrorKind taxonomy. */
class AiError(val kind: AiErrorKind, message: String) : RuntimeException(message)

interface AIProviderStream {
    /**
     * Chunked generation: yields incremental text deltas as they arrive.
     * Empty body yields no items; failures throw AiError or return an empty flow —
     * match existing error conventions in AIProvider.kt.
     */
    suspend fun generateStream(prompt: String, history: List<ChatMessage>, model: String? = null): Flow<String>
}

data class ChatMessage(
    val role: String,
    val content: String
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter> = emptyList(),
    val riskLevel: com.vasu.assistant.core.security.RiskLevel = com.vasu.assistant.core.security.RiskLevel.LOW
) {
    val requiredRole: com.vasu.assistant.core.security.UserRole
        get() = riskLevel.requiredRole
}

data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true
)
