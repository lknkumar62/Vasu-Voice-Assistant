package com.vasu.assistant.ui.location

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vasu.assistant.core.location.LocationData
import com.vasu.assistant.core.location.LocationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val currentLocation: LocationData? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationManager: LocationManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    fun getCurrentLocation() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            locationManager.getLastKnownLocation { location ->
                _uiState.value = _uiState.value.copy(
                    currentLocation = location,
                    isLoading = false,
                    error = if (location == null) "Unable to get location" else null
                )
            }
        }
    }

    fun searchNearby(type: String, context: Context) {
        val location = _uiState.value.currentLocation ?: return
        val query = when (type) {
            "restaurants" -> "restaurants near me"
            "hospitals" -> "hospitals near me"
            "gas" -> "gas stations near me"
            "hotels" -> "hotels near me"
            else -> "places near me"
        }
        // Handled via intent in LocationScreen
    }
}
