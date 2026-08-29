package com.vasu.assistant.core.offline

import com.vasu.assistant.core.automation.ActionResult
import com.vasu.assistant.core.network.NetworkMonitor
import com.vasu.assistant.core.network.NetworkState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineManager @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {
    enum class ConnectionState { ONLINE, OFFLINE, DEGRADED }

    private var listeners = mutableListOf<ConnectionListener>()

    interface ConnectionListener {
        fun onConnectionChanged(state: ConnectionState)
    }

    // Connectivity detection used to be reimplemented here with its own
    // ConnectivityManager query, so the two could disagree. NetworkMonitor is the
    // single source of truth; this only translates its state.
    fun checkConnection(): ConnectionState = getState()

    fun isOnline(): Boolean = networkMonitor.isOnline()

    fun getState(): ConnectionState = when (networkMonitor.state.value) {
        NetworkState.ONLINE -> ConnectionState.ONLINE
        NetworkState.DEGRADED -> ConnectionState.DEGRADED
        NetworkState.OFFLINE -> ConnectionState.OFFLINE
    }

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
        val state = getState()
        return ActionResult.success("connection", state.name, mapOf(
            "state" to state.name,
            "isOnline" to isOnline()
        ))
    }
}
