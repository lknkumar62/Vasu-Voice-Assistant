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
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "chat",          // "chat" | "voice"
    val isToolExecution: Boolean = false
)

/**
 * Conversation session entity — one row per chat session (FIX-5).
 */
@Entity(tableName = "conversation_sessions")
data class ConversationSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * DAO for conversation messages and sessions
 */
@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessageEntity): Long

    @Query("SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun getMessages(conversationId: String): Flow<List<ConversationMessageEntity>>

    @Query("SELECT * FROM (SELECT * FROM conversation_messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit) ORDER BY timestamp ASC")
    suspend fun getRecentMessages(conversationId: String, limit: Int = 20): List<ConversationMessageEntity>

    @Query("DELETE FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM conversation_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("SELECT COUNT(*) FROM conversation_messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    // --- Sessions (FIX-5) ---

    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<ConversationSessionEntity>>

    @Query("SELECT * FROM conversation_sessions ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestSession(): ConversationSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: ConversationSessionEntity)

    @Query("UPDATE conversation_sessions SET title = :title WHERE id = :id")
    suspend fun renameSession(id: String, title: String)

    @Query("UPDATE conversation_sessions SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touchSession(id: String, updatedAt: Long)

    @Query("DELETE FROM conversation_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
