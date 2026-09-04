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
    CLAUDE("Claude / OmniRoute"),
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
    private val claude: ClaudeProvider,
    private val keyStore: SecureKeyStore
) {
    private val _state = MutableStateFlow(AIState.IDLE)
    val state: StateFlow<AIState> = _state.asStateFlow()

    private val initialProvider = when (keyStore.selectedProvider.lowercase()) {
        "claude", "omniroute", "anthropic" -> AIProvider.CLAUDE
        "local" -> AIProvider.LOCAL
        else -> AIProvider.GEMINI
    }

    private val _currentProvider = MutableStateFlow(initialProvider)
    val currentProvider: StateFlow<AIProvider> = _currentProvider.asStateFlow()

    private val _lastError = MutableStateFlow<AiErrorKind?>(null)
    val lastError: StateFlow<AiErrorKind?> = _lastError.asStateFlow()

    /** True when a key is stored and the selected provider is enabled. */
    val isCloudReady: Boolean
        get() = when (_currentProvider.value) {
            AIProvider.GEMINI -> keyStore.geminiEnabled && keyStore.hasGeminiKey()
            AIProvider.CLAUDE -> keyStore.claudeEnabled && keyStore.hasClaudeKey()
            AIProvider.LOCAL -> false
        }

    fun setProvider(provider: AIProvider) {
        _currentProvider.value = provider
        keyStore.selectedProvider = when (provider) {
            AIProvider.GEMINI -> "gemini"
            AIProvider.CLAUDE -> "claude"
            AIProvider.LOCAL -> "local"
        }
    }

    fun saveApiKey(key: String): Boolean = when (_currentProvider.value) {
        AIProvider.GEMINI -> keyStore.setGeminiKey(key)
        AIProvider.CLAUDE -> keyStore.setClaudeKey(key)
        AIProvider.LOCAL -> false
    }

    fun removeApiKey(): Boolean = when (_currentProvider.value) {
        AIProvider.GEMINI -> keyStore.clearGeminiKey()
        AIProvider.CLAUDE -> keyStore.clearClaudeKey()
        AIProvider.LOCAL -> false
    }

    suspend fun testConnection(): AiResult = when (_currentProvider.value) {
        AIProvider.GEMINI -> gemini.testConnection()
        AIProvider.CLAUDE -> claude.testConnection()
        AIProvider.LOCAL -> AiResult.Failure(AiErrorKind.NOT_CONFIGURED, "Local provider does not use cloud connections.")
    }

    /** Reads which models the stored key may use, so the picker offers real choices. */
    suspend fun refreshModels(): ModelCatalog = gemini.refreshModels()

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
            AIProvider.CLAUDE -> claude.generate(
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
        val resolvedSystemPrompt = systemPrompt.ifBlank { defaultSystemPrompt() }

        val result = when (provider) {
            AIProvider.GEMINI -> gemini.generate(
                prompt = prompt,
                systemPrompt = resolvedSystemPrompt,
                history = history,
                tools = tools
            )
            AIProvider.CLAUDE -> claude.generate(
                prompt = prompt,
                systemPrompt = resolvedSystemPrompt,
                history = history,
                tools = tools
            )
            AIProvider.LOCAL -> AiResult.Failure(
                AiErrorKind.NOT_CONFIGURED,
                "Online AI unavailable — using offline commands."
            )
        }

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

    /**
     * Fallback persona, used only when a caller does not supply one. AIOrchestrator
     * passes the full prompt; this keeps a bare chat() call in the same voice.
     */
    private fun defaultSystemPrompt(): String = """
        तुम VASU हो। सामान्य बातचीत में हमेशा स्वाभाविक, बोलचाल की हिंदी देवनागरी लिपि में उत्तर दो। रोमन हिंदी जैसे 'kya haal hai', 'main theek hoon' का उपयोग मत करो। उत्तर ऐसे लिखो जैसे कोई भारतीय व्यक्ति स्वाभाविक रूप से बोल रहा हो।
        उत्तर संक्षिप्त (1-2 वाक्य) रखो। जब तक उपयोगकर्ता स्पष्ट रूप से अंग्रेजी में बात करने को न कहे, तुम्हारा उत्तर हिंदी देवनागरी में ही होना चाहिए।
    """.trimIndent()
}

data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String
)
