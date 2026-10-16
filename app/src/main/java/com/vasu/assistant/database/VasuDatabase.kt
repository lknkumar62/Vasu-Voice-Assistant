package com.vasu.assistant.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ConversationMessageEntity::class,
        ConversationSessionEntity::class,
        UserMemoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VasuDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        /**
         * v1 -> v2 (FIX-5): adds the conversation_sessions table and the
         * source/isToolExecution columns on messages. The messages table is
         * rebuilt rather than ALTERed so the new columns carry no SQL defaults,
         * which is what Room validates against the entity schema.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_sessions` " +
                        "(`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_messages_new` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversationId` TEXT NOT NULL, " +
                        "`role` TEXT NOT NULL, `content` TEXT NOT NULL, `toolName` TEXT, `toolResult` TEXT, " +
                        "`timestamp` INTEGER NOT NULL, `source` TEXT NOT NULL, `isToolExecution` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO `conversation_messages_new` " +
                        "(`id`, `conversationId`, `role`, `content`, `toolName`, `toolResult`, `timestamp`, `source`, `isToolExecution`) " +
                        "SELECT `id`, `conversationId`, `role`, `content`, `toolName`, `toolResult`, `timestamp`, 'chat', 0 " +
                        "FROM `conversation_messages`"
                )
                db.execSQL("DROP TABLE `conversation_messages`")
                db.execSQL("ALTER TABLE `conversation_messages_new` RENAME TO `conversation_messages`")
                // Seed one session per legacy conversationId so old messages
                // stay reachable in the new sessions list.
                db.execSQL(
                    "INSERT INTO `conversation_sessions` (`id`, `title`, `createdAt`, `updatedAt`) " +
                        "SELECT `conversationId`, 'Chat', MIN(`timestamp`), MAX(`timestamp`) " +
                        "FROM `conversation_messages` GROUP BY `conversationId`"
                )
            }
        }
    }
}
