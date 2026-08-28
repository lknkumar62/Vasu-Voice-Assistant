package com.vasu.assistant.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * User memory entity
 */
@Entity(tableName = "user_memory")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,          // e.g., "user_name", "favorite_color"
    val value: String,
    val confidence: Float = 1.0f,
    val source: String = "conversation",  // "conversation", "explicit", "inferred"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * DAO for user memory
 */
@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: UserMemoryEntity): Long

    @Query("SELECT * FROM user_memory WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): UserMemoryEntity?

    @Query("SELECT * FROM user_memory WHERE `key` LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'")
    suspend fun searchMemory(query: String): List<UserMemoryEntity>

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC")
    fun getAllMemory(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memory ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentMemory(limit: Int = 20): List<UserMemoryEntity>

    @Update
    suspend fun updateMemory(memory: UserMemoryEntity)

    @Query("DELETE FROM user_memory WHERE `key` = :key")
    suspend fun deleteMemory(key: String)

    @Query("DELETE FROM user_memory")
    suspend fun deleteAllMemory()

    @Query("SELECT COUNT(*) FROM user_memory")
    suspend fun getMemoryCount(): Int
}
