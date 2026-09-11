package com.vasu.assistant.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationMessageEntity::class,
        UserMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VasuDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
}
