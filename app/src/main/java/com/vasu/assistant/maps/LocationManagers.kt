package com.vasu.assistant.maps

import android.content.Context
import android.location.LocationManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LocationResult(
    val success: Boolean,
    val data: Map<String, Any>? = null,
    val error: String? = null,
    val message: String? = null
)

@Singleton
class VasuLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _currentLocation = MutableStateFlow("")
    val currentLocation: StateFlow<String> = _currentLocation.asStateFlow()

    suspend fun getCurrentLocation(): LocationResult {
        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val location = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)

            if (location != null) {
                val data = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "accuracy" to location.accuracy
                )
                _currentLocation.value = "Lat: ${location.latitude}, Lng: ${location.longitude}"
                LocationResult(success = true, data = data, message = "Location retrieved")
            } else {
                LocationResult(success = false, error = "No location available")
            }
        } catch (e: SecurityException) {
            LocationResult(success = false, error = "Location permission not granted")
        } catch (e: Exception) {
            LocationResult(success = false, error = e.message)
        }
    }
}

@Singleton
class PlacesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun searchNearby(type: String) {
        Log.i("PlacesManager", "Searching nearby: $type")
    }
}
