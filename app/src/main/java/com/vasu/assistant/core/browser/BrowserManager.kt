package com.vasu.assistant.core.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.i(TAG, "Opened URL: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open URL: $url", e)
        }
    }

    fun search(query: String) {
        val url = "https://www.google.com/search?q=${Uri.encode(query)}"
        openUrl(url)
    }

    companion object {
        private const val TAG = "BrowserManager"
    }
}
