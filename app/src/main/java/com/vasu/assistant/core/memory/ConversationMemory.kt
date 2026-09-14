package com.vasu.assistant.core.memory

class ConversationMemory {
    private val history = mutableListOf<Pair<String, String>>()

    fun addMessage(role: String, content: String) {
        history.add(role to content)
    }

    fun getFormattedHistory(limit: Int): String {
        return history.takeLast(limit).joinToString("\n") { "${it.first}: ${it.second}" }
    }

    fun newConversation(): String {
        history.clear()
        return "Conversation reset"
    }

    fun clearCurrent() {
        history.clear()
    }

    fun getRecentContext(): String {
        return getFormattedHistory(5)
    }
}
