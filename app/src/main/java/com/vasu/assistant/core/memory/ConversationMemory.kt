package com.vasu.assistant.core.memory

import com.vasu.assistant.database.ConversationMessageEntity
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

/**
 * ConversationMemory - Manages conversation context and history.
 *
 * Maintains short-term conversation context for AI interactions.
 * Supports multiple concurrent conversations.
 */
@Singleton
class ConversationMemory @Inject constructor(
    private val repository: MemoryRepository
) {
    private val activeConversations = mutableMapOf<String, MutableList<ConversationMessageEntity>>()
    private var currentConversationId: String = UUID.randomUUID().toString()

    /**
     * Get or create a new conversation
     */
    fun newConversation(): String {
        currentConversationId = UUID.randomUUID().toString()
        activeConversations[currentConversationId] = mutableListOf()
        return currentConversationId
    }

    /**
     * Get current conversation ID
     */
    fun getCurrentConversationId(): String = currentConversationId

    /**
     * Add a message to conversation
     */
    suspend fun addMessage(role: String, content: String, toolName: String? = null, toolResult: String? = null) {
        val message = ConversationMessageEntity(
            conversationId = currentConversationId,
            role = role,
            content = content,
            toolName = toolName,
            toolResult = toolResult
        )

        // Add to active conversation
        activeConversations.getOrPut(currentConversationId) { mutableListOf() }.add(message)

        // Save to database
        repository.saveMessage(currentConversationId, role, content, toolName, toolResult)
    }

    /**
     * Get conversation history for AI context
     */
    suspend fun getConversationHistory(limit: Int = 20): List<ConversationMessageEntity> {
        // First check active conversation
        val activeMessages = activeConversations[currentConversationId]
        if (!activeMessages.isNullOrEmpty() && activeMessages.size <= limit) {
            return activeMessages
        }

        // Fallback to database
        return repository.getRecentMessages(currentConversationId, limit)
    }

    /**
     * Get formatted conversation for AI prompt
     */
    suspend fun getFormattedHistory(limit: Int = 20): String {
        val messages = getConversationHistory(limit)
        return messages.joinToString("\n") { msg ->
            "${msg.role}: ${msg.content}"
        }
    }

    /**
     * Clear current conversation
     */
    fun clearCurrent() {
        activeConversations[currentConversationId]?.clear()
    }

    /**
     * Delete a conversation
     */
    suspend fun deleteConversation(conversationId: String) {
        activeConversations.remove(conversationId)
        repository.deleteConversation(conversationId)
    }

    /**
     * Get message count
     */
    suspend fun getMessageCount(): Int {
        return repository.getMessageCount(currentConversationId)
    }
}
