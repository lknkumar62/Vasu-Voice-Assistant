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
 * Network state.
 *
 * DEGRADED means a network is attached but has not been validated - a captive
 * portal, or Wi-Fi with no working uplink. Requests will fail, so it is worth
 * distinguishing from OFFLINE when explaining a failure to the user.
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
            _state.value = classify(connectivityManager.getNetworkCapabilities(network))
        }

        override fun onLost(network: Network) {
            _state.value = NetworkState.OFFLINE
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _state.value = classify(capabilities)
        }
    }

    init {
        startMonitoring()
    }

    /**
     * A metered connection is still a working connection. Treating "metered" as
     * DEGRADED meant ordinary mobile data reported as not-online, which made the
     * app claim to be offline on cellular.
     */
    private fun classify(capabilities: NetworkCapabilities?): NetworkState = when {
        capabilities == null -> NetworkState.OFFLINE
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.OFFLINE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkState.ONLINE
        else -> NetworkState.DEGRADED
    }

    private fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Check initial state
        _state.value = classify(
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        )
    }

    fun isOnline(): Boolean = _state.value == NetworkState.ONLINE
    fun isOffline(): Boolean = _state.value == NetworkState.OFFLINE

    fun stopMonitoring() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
