package com.vasu.assistant.core.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun search(query: String) {
        val searchUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
        openUrl(searchUrl)
    }

    fun openYouTube(query: String) {
        val url = "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
        openUrl(url)
    }

    fun openMaps(query: String) {
        val url = "https://www.google.com/maps/search/${Uri.encode(query)}"
        openUrl(url)
    }

    fun openEmail(recipient: String, subject: String = "", body: String = "") {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$recipient")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        context.startActivity(intent)
    }
}
