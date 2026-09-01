package com.vasu.assistant.core.offlinemode

import android.content.Context
import androidx.core.content.ContextCompat
import com.vasu.assistant.core.network.NetworkMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) {
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    fun canUseAiFeatures(): Boolean = isOnline.value

    fun getOfflineFeatures(): List<String> = listOf(
        "Device controls (torch, volume, media)",
        "File management",
        "Local automation and missions",
        "Voice commands (device control only)"
    )

    fun getOnlineFeatures(): List<String> = listOf(
        "Gemini AI chat",
        "Voice commands (all types)",
        "Location and maps",
        "Online search"
    )
}
