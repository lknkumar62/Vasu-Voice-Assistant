package com.vasu.ai.memory

class VasuMemoryPromptSection {

    fun build(context: VasuMemoryConversationContext): String {
        val memory = context.memoryText.trim()

        if (memory.isBlank()) {
            return ""
        }

        return buildString {
            append("[VASU_MEMORY]\n")
            append(memory)
            append("\n[/VASU_MEMORY]")
        }
    }
}
