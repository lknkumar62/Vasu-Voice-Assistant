package com.vasu.assistant.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "user_memories")
data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val confidence: Float = 1.0f,
    val source: String = "user",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface MemoryDao {
    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC")
    fun getAllMemory(): Flow<List<UserMemoryEntity>>

    @Query("SELECT * FROM user_memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemory(key: String): UserMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(entity: UserMemoryEntity): Long

    @Update
    suspend fun updateMemory(entity: UserMemoryEntity)

    @Query("SELECT * FROM user_memories WHERE `key` LIKE '%' || :query || '%' OR value LIKE '%' || :query || '%'")
    suspend fun searchMemory(query: String): List<UserMemoryEntity>

    @Query("DELETE FROM user_memories WHERE `key` = :key")
    suspend fun deleteMemory(key: String)

    @Query("SELECT * FROM user_memories ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentMemory(limit: Int = 50): List<UserMemoryEntity>

    @Query("DELETE FROM user_memories")
    suspend fun deleteAllMemory()

    @Query("SELECT COUNT(*) FROM user_memories")
    suspend fun getMemoryCount(): Int
}
