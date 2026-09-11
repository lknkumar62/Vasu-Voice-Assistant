package com.vasu.assistant.core.ai

import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIClient @Inject constructor(
    private val geminiProvider: GeminiProvider,
    private val claudeProvider: ClaudeProvider,
    private val keyStore: SecureKeyStore
) {
    private val _currentProvider = kotlinx.coroutines.flow.MutableStateFlow(AIProvider.GEMINI)
    val currentProvider: StateFlow<AIProvider> = _currentProvider

    fun setProvider(provider: AIProvider) {
        _currentProvider.value = provider
        keyStore.selectedProvider = provider.name
    }

    fun saveApiKey(key: String): Boolean = keyStore.setGeminiKey(key)
    fun removeApiKey() { keyStore.clearGeminiKey() }

    suspend fun testConnection(): AiResult {
        return when (_currentProvider.value) {
            AIProvider.GEMINI -> geminiProvider.generate(prompt = "Hello, respond with just 'OK'")
            AIProvider.CLAUDE -> claudeProvider.generate(prompt = "Hello, respond with just 'OK'")
            AIProvider.LOCAL -> AiResult.Text("Local mode active", 0)
        }
    }

    suspend fun refreshModels(): ModelCatalog {
        return geminiProvider.refreshModels()
    }
}
