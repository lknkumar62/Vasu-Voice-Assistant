package com.vasu.assistant.core.ai

enum class AIProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    CLAUDE("Anthropic Claude"),
    LOCAL("Local (Offline Only)")
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
