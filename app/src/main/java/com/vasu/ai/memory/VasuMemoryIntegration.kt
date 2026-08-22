package com.vasu.ai.memory

class VasuMemoryIntegration(
    private val contextBuilder: VasuMemoryConversationContextBuilder,
    private val promptSection: VasuMemoryPromptSection =
        VasuMemoryPromptSection()
) {

    fun buildPromptMemory(maxEntries: Int = 10): String {
        val context = contextBuilder.build(maxEntries)
        return promptSection.build(context)
    }
}
