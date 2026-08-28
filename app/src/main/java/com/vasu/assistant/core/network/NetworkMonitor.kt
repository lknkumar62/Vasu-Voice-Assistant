package com.vasu.assistant.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network state
 */
enum class NetworkState {
    ONLINE, OFFLINE, DEGRADED
}

/**
 * NetworkMonitor - Monitors network connectivity.
 *
 * Tracks online/offline state for AI provider selection
 * and offline mode decisions.
 */
@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow(NetworkState.OFFLINE)
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _state.value = NetworkState.ONLINE
        }

        override fun onLost(network: Network) {
            _state.value = NetworkState.OFFLINE
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val isMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED).not()
            _state.value = if (isMetered) NetworkState.DEGRADED else NetworkState.ONLINE
        }
    }

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        _state.value = if (capabilities != null) NetworkState.ONLINE else NetworkState.OFFLINE
    }

    fun isOnline(): Boolean = _state.value == NetworkState.ONLINE
    fun isOffline(): Boolean = _state.value == NetworkState.OFFLINE

    fun stopMonitoring() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
