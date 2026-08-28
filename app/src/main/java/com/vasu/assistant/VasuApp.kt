package com.vasu.assistant

import android.app.Application
import androidx.room.Room
import com.vasu.assistant.database.ConversationDao
import com.vasu.assistant.database.MemoryDao
import com.vasu.assistant.database.VasuDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VasuApp : Application() {

    lateinit var database: VasuDatabase
        private set
    lateinit var conversationDao: ConversationDao
        private set
    lateinit var memoryDao: MemoryDao
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Room database
        database = Room.databaseBuilder(
            applicationContext,
            VasuDatabase::class.java,
            "vasu_database"
        ).build()

        conversationDao = database.conversationDao()
        memoryDao = database.memoryDao()
    }

    companion object {
        lateinit var instance: VasuApp
            private set
    }
}
