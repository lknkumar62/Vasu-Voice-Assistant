package com.vasu.assistant.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vasu.assistant.core.browser.BrowserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val browserManager: BrowserManager
) : ViewModel() {
    fun openUrl(url: String, context: Context) {
        browserManager.openUrl(url)
    }

    fun search(query: String, context: Context) {
        browserManager.search(query)
    }
}
