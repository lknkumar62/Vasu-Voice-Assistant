package com.vasu.assistant.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI providers VASU can actually reach.
 *
 * OPENAI, CLAUDE and GROQ used to be listed here but every one of them returned a
 * hardcoded "will be connected in production" string. They were removed rather
 * than left in the picker, because offering a provider that cannot answer is
 * indistinguishable from a broken app.
 */
enum class AIProvider(val displayName: String) {
    GEMINI("Google Gemini"),
    LOCAL("Offline commands")
}

data class AIRequest(
    val prompt: String,
    val systemPrompt: String = "",
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val provider: AIProvider = AIProvider.GEMINI
)

data class AIResponse(
    val content: String,
    val toolCalls: List<ToolCall> = emptyList(),
    val provider: AIProvider,
    val tokensUsed: Int = 0,
    val latencyMs: Long = 0,
    /** Null on success. Set so callers can tell "offline" from "bad key". */
    val error: AiErrorKind? = null
) {
    val isSuccess: Boolean get() = error == null
}

data class ToolCall(
    val name: String,
    val parameters: Map<String, Any>
)

enum class AIState {
    IDLE, THINKING, TOOL_EXECUTING, ERROR
}

@Singleton
class AIClient @Inject constructor(
    private val gemini: GeminiProvider,
    private val keyStore: SecureKeyStore
) {
    private val _state = MutableStateFlow(AIState.IDLE)
    val state: StateFlow<AIState> = _state.asStateFlow()

    private val _currentProvider = MutableStateFlow(AIProvider.GEMINI)
    val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _lastError = MutableStateFlow<AiErrorKind?>(null)
    val lastError: StateFlow<AiErrorKind?> = _lastError.asStateFlow()

    /** True only when a key is stored and the user has switched Gemini on. */
    val isCloudReady: Boolean
        get() = keyStore.geminiEnabled && keyStore.hasGeminiKey()

    fun setProvider(provider: AIProvider) { _currentProvider.value = provider }

    fun saveApiKey(key: String): Boolean = keyStore.setGeminiKey(key)

    fun removeApiKey(): Boolean = keyStore.clearGeminiKey()

    suspend fun testConnection(): AiResult = gemini.testConnection()

    suspend fun chat(request: AIRequest): AIResponse {
        _state.value = AIState.THINKING
        val startTime = System.currentTimeMillis()

        val result = when (request.provider) {
            AIProvider.GEMINI -> gemini.generate(
                prompt = request.prompt,
                systemPrompt = request.systemPrompt,
                temperature = request.temperature,
                maxTokens = request.maxTokens
            )
            AIProvider.LOCAL -> AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "Online AI unavailable — using offline commands."
            )
        }

        return result.toResponse(request.provider, System.currentTimeMillis() - startTime)
    }

    /**
     * Asks the model to pick a tool. The model can only name a tool from [tools];
     * the returned call is still validated and permission-gated by ToolRouter
     * before anything runs, so a hallucinated name fails closed.
     */
    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>,
        systemPrompt: String = "",
        provider: AIProvider = _currentProvider.value
    ): AIResponse {
        _state.value = AIState.THINKING
        val startTime = System.currentTimeMillis()

        if (provider == AIProvider.LOCAL) {
            return AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "Online AI unavailable — using offline commands."
            ).toResponse(provider, System.currentTimeMillis() - startTime)
        }

        val prompt = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        val history = messages.dropLast(1)

        val result = gemini.generate(
            prompt = prompt,
            systemPrompt = systemPrompt.ifBlank { defaultSystemPrompt() },
            history = history,
            tools = tools
        )

        return result.toResponse(provider, System.currentTimeMillis() - startTime)
    }

    private fun AiResult.toResponse(provider: AIProvider, latency: Long): AIResponse = when (this) {
        is AiResult.Text -> {
            _state.value = AIState.IDLE
            _lastError.value = null
            AIResponse(content, provider = provider, tokensUsed = tokensUsed, latencyMs = latency)
        }
        is AiResult.FunctionCall -> {
            _state.value = AIState.TOOL_EXECUTING
            _lastError.value = null
            AIResponse(
                content = "",
                toolCalls = listOf(ToolCall(name, args)),
                provider = provider,
                tokensUsed = tokensUsed,
                latencyMs = latency
            )
        }
        is AiResult.Failure -> {
            _state.value = AIState.ERROR
            _lastError.value = kind
            AIResponse(content = message, provider = provider, latencyMs = latency, error = kind)
        }
    }

    private fun defaultSystemPrompt(): String = """
        You are VASU, a warm and friendly Android voice assistant.
        Reply in the same language the user speaks: Hindi, English, or Hinglish.
        Keep spoken replies short and natural, one or two sentences.
        Use a tool when the user asks you to do something on the phone.
        Never claim an action succeeded unless a tool result says so.
    """.trimIndent()
}

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)
