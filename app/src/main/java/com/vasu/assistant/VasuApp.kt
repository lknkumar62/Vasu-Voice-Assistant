package com.vasu.assistant

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VasuApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: VasuApp
            private set
    }
}
