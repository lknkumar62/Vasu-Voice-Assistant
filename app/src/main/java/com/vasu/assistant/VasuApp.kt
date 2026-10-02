package com.vasu.assistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VasuApp : Application() {

    private var previousUncaught: Thread.UncaughtExceptionHandler? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.vasu.assistant.core.logging.ErrorLog.init(this)
        previousUncaught = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            com.vasu.assistant.core.logging.ErrorLog.log("UNCAUGHT", "thread=${thread.name}: ${throwable.message}", throwable)
            previousUncaught?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: VasuApp
            private set
    }
}
