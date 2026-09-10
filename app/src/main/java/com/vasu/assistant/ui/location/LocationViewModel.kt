package com.vasu.assistant.ui.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.maps.PlacesManager
import com.vasu.assistant.maps.VasuLocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LocationUiState(
    val currentLocation: String = "Tap refresh to get location",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationManager: VasuLocationManager,
    private val placesManager: PlacesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun getCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = withContext(Dispatchers.IO) {
                locationManager.getCurrentLocation()
            }
            if (result.success && result.data != null) {
                val lat = (result.data["lat"] as? Number)?.toDouble() ?: 0.0
                val lng = (result.data["lng"] as? Number)?.toDouble() ?: 0.0
                val addr = (result.data["address"] as? String) ?: "$lat, $lng"
                _uiState.value = _uiState.value.copy(
                    currentLocation = addr,
                    latitude = lat,
                    longitude = lng,
                    isLoading = false,
                    error = null
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.error ?: result.message
                )
            }
        }
    }

    fun searchNearby(type: String, context: Context) {
        placesManager.searchNearby(type)
    }
}
