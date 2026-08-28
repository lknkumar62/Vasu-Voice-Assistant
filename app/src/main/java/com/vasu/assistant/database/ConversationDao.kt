package com.vasu.assistant.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Conversation message entity
 */
@Entity(tableName = "conversation_messages")
data class ConversationMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,         // "user", "assistant", "system"
    val content: String,
    val toolName: String? = null,
    val toolResult: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * DAO for conversation messages
 */
@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int = 20): List<ConversationMessageEntity>

    @Query("DELETE FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversation_messages")
    suspend fun deleteAllMessages()

    @Query("SELECT COUNT(*) FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    @Query("SELECT * FROM conversation_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getGlobalRecentMessages(limit: Int = 50): List<ConversationMessageEntity>
}
