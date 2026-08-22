package com.vasu.ai.memory

class VasuMemoryConversationContextBuilder(
    private val provider: VasuMemoryContextProvider
) {

    fun build(maxEntries: Int = 10): VasuMemoryConversationContext {
        return VasuMemoryConversationContext(
            memoryText = provider.getContext(maxEntries)
        )
    }
}
