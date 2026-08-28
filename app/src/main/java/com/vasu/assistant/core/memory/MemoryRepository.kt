package com.vasu.assistant.core.memory

import com.vasu.assistant.database.ConversationDao
import com.vasu.assistant.database.ConversationMessageEntity
import com.vasu.assistant.database.MemoryDao
import com.vasu.assistant.database.UserMemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MemoryRepository - Data access layer for memory system.
 *
 * Abstracts database operations for conversations and user memory.
 */
@Singleton
class MemoryRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val memoryDao: MemoryDao
) {
    // Conversation operations

    suspend fun saveMessage(
        conversationId: String,
        role: String,
        content: String,
        toolName: String? = null,
        toolResult: String? = null
    ): Long {
        val entity = ConversationMessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            toolName = toolName,
            toolResult = toolResult
        )
        return conversationDao.insertMessage(entity)
    }

    suspend fun getRecentMessages(conversationId: String, limit: Int = 20): List<ConversationMessageEntity> {
        return conversationDao.getRecentMessages(conversationId, limit)
    }

    suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
    }

    suspend fun getMessageCount(conversationId: String): Int {
        return conversationDao.getMessageCount(conversationId)
    }

    // User memory operations

    suspend fun remember(key: String, value: String, source: String = "conversation"): Long {
        val existing = memoryDao.getMemory(key)
        return if (existing != null) {
            val updated = existing.copy(
                value = value,
                updatedAt = System.currentTimeMillis(),
                confidence = minOf(existing.confidence + 0.1f, 1.0f)
            )
            memoryDao.updateMemory(updated)
            existing.id
        } else {
            val entity = UserMemoryEntity(
                key = key,
                value = value,
                source = source
            )
            memoryDao.insertMemory(entity)
        }
    }

    suspend fun recall(key: String): String? {
        return memoryDao.getMemory(key)?.value
    }

    suspend fun searchMemory(query: String): List<UserMemoryEntity> {
        return memoryDao.searchMemory(query)
    }

    suspend fun forget(key: String) {
        memoryDao.deleteMemory(key)
    }

    suspend fun getAllMemory(): List<UserMemoryEntity> {
        return memoryDao.getRecentMemory(100)
    }

    suspend fun getMemoryCount(): Int {
        return memoryDao.getMemoryCount()
    }
}
