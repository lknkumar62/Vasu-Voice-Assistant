package com.vasu.assistant.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VasuLocationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private var lastKnownLocation: Location? = null
    private var locationHistory = mutableListOf<LocationRecord>()

    data class LocationRecord(
        val lat: Double, val lng: Double, val address: String, val timestamp: Long
    )

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentLocation(): ActionResult {
        if (!hasPermission()) return ActionResult.error("location", "Location permission not granted", "No location permission")
        return try {
            val latch = CountDownLatch(1)
            var result: ActionResult = ActionResult.error("location", "Location request timed out", "Timeout")
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).setMaxUpdates(1).build()
            fusedClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        lastKnownLocation = location
                        val addr = getAddress(location.latitude, location.longitude)
                        result = ActionResult.success("location", "Current location", mapOf(
                            "lat" to location.latitude, "lng" to location.longitude,
                            "address" to addr, "accuracy" to location.accuracy
                        ))
                    } else {
                        result = ActionResult.error("location", "Could not get location", "Location is null")
                    }
                    latch.countDown()
                }
            }, Looper.getMainLooper())
            latch.await(8, TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            ActionResult.error("location", "Failed to get location", e.message ?: "Unknown")
        }
    }

    fun saveParkingLocation(): ActionResult {
        val loc = lastKnownLocation ?: return ActionResult.error("parking", "No location available", "Get location first")
        val addr = getAddress(loc.latitude, loc.longitude)
        locationHistory.add(LocationRecord(loc.latitude, loc.longitude, "Parking - $addr", System.currentTimeMillis()))
        return ActionResult.success("parking", "Parking saved: $addr", mapOf("lat" to loc.latitude, "lng" to loc.longitude))
    }

    fun openNavigation(destination: String): ActionResult {
        return try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("google.navigation:q=${java.net.URLEncoder.encode(destination, "UTF-8")}")
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ActionResult.success("navigate", "Opening navigation to $destination")
        } catch (e: Exception) {
            ActionResult.error("navigate", "Failed to open navigation", e.message ?: "Unknown")
        }
    }

    fun getLocationHistory(): ActionResult {
        val records = locationHistory.map { mapOf("lat" to it.lat, "lng" to it.lng, "address" to it.address, "time" to it.timestamp) }
        return ActionResult.success("history", "Found ${records.size} locations", mapOf("locations" to records))
    }

    fun getParkingLocation(): ActionResult {
        val parking = locationHistory.lastOrNull { it.address.startsWith("Parking") }
            ?: return ActionResult.error("parking", "No saved parking location found", "NOT_FOUND")
        return ActionResult.success("parking", "Parking location: ${parking.address}", mapOf("lat" to parking.lat, "lng" to parking.lng, "address" to parking.address, "time" to parking.timestamp))
    }

    fun getTrafficInfo(destination: String): ActionResult {
        val destQuery = if (destination.isNotBlank()) destination else "current route"
        return try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("google.navigation:q=${java.net.URLEncoder.encode(destQuery, "UTF-8")}&layer=t")
            ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
            ActionResult.success("traffic", "Opening traffic view for $destQuery", mapOf("destination" to destQuery))
        } catch (e: Exception) {
            ActionResult.error("traffic", "Failed to open traffic view: ${e.message}", "TRAFFIC_FAILED")
        }
    }

    private fun getAddress(lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                addresses[0].getAddressLine(0) ?: "$lat, $lng"
            } else "$lat, $lng"
        } catch (e: Exception) {
            "$lat, $lng"
        }
    }
}
