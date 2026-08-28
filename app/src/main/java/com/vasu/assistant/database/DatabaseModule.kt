package com.vasu.assistant.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VasuDatabase {
        return Room.databaseBuilder(
            context,
            VasuDatabase::class.java,
            "vasu_database"
        ).build()
    }

    @Provides
    fun provideConversationDao(database: VasuDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideMemoryDao(database: VasuDatabase): MemoryDao {
        return database.memoryDao()
    }
}
