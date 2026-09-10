package com.vasu.assistant.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkState {
    ONLINE,
    DEGRADED,
    OFFLINE
}

@Singleton
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow(checkConnectivityState())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    private val _isOnline = MutableStateFlow(_state.value == NetworkState.ONLINE)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private fun checkConnectivityState(): NetworkState {
        val network = connectivityManager.activeNetwork ?: return NetworkState.OFFLINE
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkState.OFFLINE
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            hasInternet && validated -> NetworkState.ONLINE
            hasInternet -> NetworkState.DEGRADED
            else -> NetworkState.OFFLINE
        }
    }

    fun updateConnectivity() {
        val newState = checkConnectivityState()
        _state.value = newState
        _isOnline.value = newState == NetworkState.ONLINE
    }
}
