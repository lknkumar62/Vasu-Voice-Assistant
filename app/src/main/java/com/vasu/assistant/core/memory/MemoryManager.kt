package com.vasu.assistant.core.memory

import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryManager - Main coordinator for all memory systems.
 *
 * Coordinates:
 * - ConversationMemory (short-term context)
 * - UserMemory (long-term facts)
 *
 * Provides unified API for memory operations.
 */
@Singleton
class MemoryManager @Inject constructor(
    private val conversationMemory: ConversationMemory,
    private val userMemory: UserMemory
) {
    /**
     * Process user input and extract learnable information
     */
    suspend fun processInput(input: String) {
        // Learn from conversation
        userMemory.learnFromConversation(input)

        // Handle explicit memory commands
        when {
            input.lowercase().startsWith("remember that") || input.lowercase().startsWith("yaad rakh") -> {
                val fact = input.lowercase().substringAfter("that").substringAfter("rakh").trim()
                if (fact.isNotBlank()) {
                    userMemory.remember("fact_${System.currentTimeMillis()}", fact)
                }
            }
            input.lowercase().startsWith("forget that") || input.lowercase().startsWith("bhool jao") -> {
                val key = input.lowercase().substringAfter("that").substringAfter("jao").trim()
                if (key.isNotBlank()) {
                    userMemory.forget(key)
                }
            }
            input.lowercase().contains("what do you remember") || input.lowercase().contains("kya yaad hai") -> {
                // This will be handled by AI orchestrator
            }
        }
    }

    /**
     * Get full context for AI
     */
    suspend fun getFullContext(): String {
        val conversationContext = conversationMemory.getFormattedHistory(10)
        val userContext = userMemory.getMemoryContext()

        return buildString {
            if (userContext.isNotBlank()) {
                append(userContext)
                append("\n\n")
            }
            if (conversationContext.isNotBlank()) {
                append("Recent conversation:\n")
                append(conversationContext)
            }
        }
    }

    /**
     * Add message to conversation
     */
    suspend fun addMessage(role: String, content: String) {
        conversationMemory.addMessage(role, content)
    }

    /**
     * Start new conversation
     */
    fun newConversation(): String {
        return conversationMemory.newConversation()
    }

    /**
     * Remember a fact
     */
    suspend fun remember(key: String, value: String): Boolean {
        return userMemory.remember(key, value)
    }

    /**
     * Recall a fact
     */
    suspend fun recall(key: String): String? {
        return userMemory.recall(key)
    }

    /**
     * Search memory
     */
    suspend fun search(query: String): String {
        val results = userMemory.search(query)
        return if (results.isEmpty()) {
            "I don't remember anything about that."
        } else {
            results.joinToString("\n") { "- ${it.key}: ${it.value}" }
        }
    }

    /**
     * Clear current conversation
     */
    fun clearConversation() {
        conversationMemory.clearCurrent()
    }
}
