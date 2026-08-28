package com.vasu.assistant.core.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    enum class ConnectionState { ONLINE, OFFLINE, DEGRADED }

    private var state: ConnectionState = ConnectionState.OFFLINE
    private var listeners = mutableListOf<ConnectionListener>()

    interface ConnectionListener {
        fun onConnectionChanged(state: ConnectionState)
    }

    fun checkConnection(): ConnectionState {
        state = if (isOnline()) ConnectionState.ONLINE else ConnectionState.OFFLINE
        return state
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun getState(): ConnectionState = state

    fun addListener(listener: ConnectionListener) { listeners.add(listener) }
    fun removeListener(listener: ConnectionListener) { listeners.remove(listener) }

    fun getOfflineCapabilities(): ActionResult {
        val capabilities = listOf(
            "Wake word detection",
            "Voice activity detection",
            "Speaker verification",
            "Basic device controls (torch, volume, media)",
            "TTS (if voice installed)",
            "Local memory access",
            "File browsing",
            "Predefined commands",
            "Camera (photo/video)",
            "Smart modes"
        )
        return ActionResult.success("offline", "Offline capabilities", mapOf("capabilities" to capabilities))
    }

    fun getOnlineOnlyFeatures(): ActionResult {
        val features = listOf(
            "Advanced LLM reasoning",
            "Web search",
            "Cloud AI APIs",
            "Navigation/maps",
            "PC connect",
            "External OCR processing"
        )
        return ActionResult.success("online_only", "Online-only features", mapOf("features" to features))
    }

    fun getStatus(): ActionResult {
        checkConnection()
        return ActionResult.success("connection", state.name, mapOf(
            "state" to state.name,
            "isOnline" to isOnline()
        ))
    }
}
