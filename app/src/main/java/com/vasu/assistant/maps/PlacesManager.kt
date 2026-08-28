package com.vasu.assistant.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlacesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun searchNearby(query: String): ActionResult {
        return try {
            val uri = Uri.parse("geo:0,0?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("places", "Searching nearby: $query")
        } catch (e: Exception) {
            ActionResult.error("places", "Search failed", e.message ?: "Unknown")
        }
    }

    fun openInMaps(lat: Double, lng: Double, label: String = ""): ActionResult {
        return try {
            val query = if (label.isNotEmpty()) "$label@$lat,$lng" else "$lat,$lng"
            val uri = Uri.parse("geo:0,0?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("maps", "Opened in Maps: $label ($lat, $lng)")
        } catch (e: Exception) {
            ActionResult.error("maps", "Failed to open maps", e.message ?: "Unknown")
        }
    }

    fun openDirections(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): ActionResult {
        return try {
            val uri = Uri.parse("https://www.google.com/maps/dir/$fromLat,$fromLng/$toLat,$toLng")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("directions", "Opening directions")
        } catch (e: Exception) {
            ActionResult.error("directions", "Failed", e.message ?: "Unknown")
        }
    }

    fun searchOnMap(query: String): ActionResult {
        return try {
            val uri = Uri.parse("https://www.google.com/maps/search/${java.net.URLEncoder.encode(query, "UTF-8")}")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("map_search", "Searching on map: $query")
        } catch (e: Exception) {
            ActionResult.error("map_search", "Search failed", e.message ?: "Unknown")
        }
    }
}
