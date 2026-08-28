package com.vasu.assistant.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Provider types
 */
enum class AIProvider(val displayName: String) {
    OPENAI("OpenAI"),
    CLAUDE("Claude"),
    GROQ("Groq"),
    LOCAL("Local/Offline")
}

/**
 * AI request
 */
data class AIRequest(
    val prompt: String,
    val systemPrompt: String = "",
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val provider: AIProvider = AIProvider.OPENAI
)

/**
 * AI response
 */
data class AIResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val provider: AIProvider,
    val tokensUsed: Int = 0,
    val latencyMs: Long = 0
)

data class ToolCall(
    val name: String,
    val parameters: Map<String, Any>
)

/**
 * AI State
 */
enum class AIState {
    IDLE, THINKING, TOOL_EXECUTING, ERROR
}

/**
 * AIClient - Provider-agnostic AI client.
 *
 * Supports multiple AI providers through a unified interface.
 * Routes requests to the configured provider and handles responses.
 */
@Singleton
class AIClient @Inject constructor() {

    private val _state = MutableStateFlow(AIState.IDLE)
    val state: StateFlow<AIState> = _state.asStateFlow()

    private val _currentProvider = MutableStateFlow(AIProvider.OPENAI)
    val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _apiKey = MutableStateFlow<String?>(null)
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    /**
     * Set AI provider
     */
    fun setProvider(provider: AIProvider) {
        _currentProvider.value = provider
    }

    /**
     * Set API key
     */
    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    /**
     * Send a chat completion request
     */
    suspend fun chat(request: AIRequest): AIResponse {
        _state.value = AIState.THINKING
        val startTime = System.currentTimeMillis()

        return try {
            val response = when (request.provider) {
                AIProvider.OPENAI -> callOpenAI(request)
                AIProvider.CLAUDE -> callClaude(request)
                AIProvider.GROQ -> callGroq(request)
                AIProvider.LOCAL -> callLocal(request)
            }

            _state.value = AIState.IDLE
            response.copy(latencyMs = System.currentTimeMillis() - startTime)
        } catch (e: Exception) {
            _state.value = AIState.ERROR
            AIResponse(
                content = "Error: ${e.message}",
                provider = request.provider,
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Send a message and get response with tool calls
     */
    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        provider: AIProvider = _currentProvider.value
    ): AIResponse {
        _state.value = AIState.THINKING

        val request = AIRequest(
            prompt = messages.lastOrNull()?.content ?: "",
            systemPrompt = buildSystemPrompt(tools),
            provider = provider
        )

        return chat(request)
    }

    private fun buildSystemPrompt(tools: List<ToolDefinition>): String {
        val toolDescriptions = tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description} (Risk: ${tool.riskLevel})"
        }

        return """You are VASU, an AI voice assistant for Android.
You have access to the following tools:
$toolDescriptions

When the user asks you to do something, respond with the appropriate tool call.
Always respond in the same language the user speaks (Hindi, English, or Hinglish).
Be helpful, concise, and friendly."""
    }

    // Provider implementations (simplified - real implementations would use HTTP)

    private suspend fun callOpenAI(request: AIRequest): AIResponse {
        // Phase 6: Simplified mock - will connect to real API
        return AIResponse(
            content = "[OpenAI] I received: \"${request.prompt}\"\n\nAI provider will be connected in production. For now, this is a simulated response from VASU.",
            provider = AIProvider.OPENAI,
            tokensUsed = 50
        )
    }

    private suspend fun callClaude(request: AIRequest): AIResponse {
        return AIResponse(
            content = "[Claude] I received: \"${request.prompt}\"\n\nClaude provider will be connected in production.",
            provider = AIProvider.CLAUDE,
            tokensUsed = 50
        )
    }

    private suspend fun callGroq(request: AIRequest): AIResponse {
        return AIResponse(
            content = "[Groq] I received: \"${request.prompt}\"\n\nGroq provider will be connected in production.",
            provider = AIProvider.GROQ,
            tokensUsed = 50
        )
    }

    private suspend fun callLocal(request: AIRequest): AIResponse {
        return AIResponse(
            content = "[Local] I received: \"${request.prompt}\"\n\nOffline command engine will be implemented in Phase 17.",
            provider = AIProvider.LOCAL,
            tokensUsed = 0
        )
    }
}

/**
 * Chat message for AI context
 */
data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)
